package com.dateroem.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.dateroem.backend.dto.AiChatReply;

/**
 * "i may have pasted a piece of data but the system can check and provide
 * better arrangement for the user ... if one did not provide the proper
 * arrangement due to a hurry" - rearranges a long product description into
 * a clean, professional layout for the website (PRODUCTS.Description_Display,
 * migration 028), using the same AI provider as the chat assistant.
 *
 * The seller's own text (Description) is never changed. The AI may only
 * rearrange and tidy it - every result is checked against the original
 * (same numbers, nothing dropped, nothing invented) and thrown away if it
 * fails, in which case the website's own built-in tidy-up of the original
 * is what customers see.
 *
 * Runs in the background: a save never waits for the AI. A periodic sweep
 * picks up anything missed (the AI busy at the time, existing products).
 */
@Service
public class DescriptionFormatter {

    private static final Logger log = LoggerFactory.getLogger(DescriptionFormatter.class);

    // Same "long" rule as the website's isLongDescription - shorter ones are
    // shown as typed, beside the price.
    private static final int FORMAT_FROM = 220;
    // Keeps one request well inside the free tier's per-minute allowance
    // (and the reply inside its token budget); longer ones rely on the
    // website's built-in tidy-up.
    private static final int AI_MAX_INPUT = 3_500;
    // Between AI calls in a sweep, so a backlog never starves the chat
    // assistant of its per-minute allowance.
    private static final long PAUSE_BETWEEN_MS = 8_000;

    private static final String INSTRUCTIONS = """
        You tidy up product descriptions for Dateroem, a Kenyan timber and \
        wood business. The seller typed or pasted the text below in a hurry. \
        Rearrange it into a clean, professional layout that a customer can \
        scan quickly.

        Rules:
        - Use ONLY the information in the text. Never add a fact, number, \
        size, price, claim or selling point that isn't there, and never drop \
        one - only exact repeats may go.
        - Copy every number, measurement and unit exactly as written (2x4, \
        12ft, 15%, KES 3,000); never convert or round them.
        - Fix spelling, capital letters and punctuation. Keep the seller's \
        own wording wherever it is already clear.
        - Keep the language of the text (English or Kiswahili).

        Layout (plain text only):
        - Start with one or two short paragraphs saying what the product is. \
        No heading before them.
        - Then group the remaining details under headings. A heading is its \
        own line ending with a colon, chosen from what the text actually \
        covers, e.g. "Key features:", "Specifications:", "Sizes available:", \
        "Uses:", "Care and storage:", "Delivery:". Never an empty heading.
        - Under a heading: one "- " bullet per line; or for measurable \
        details (length, width, grade, moisture, origin, treatment) one \
        "Label: value" per line; or "1. " numbered lines for steps.
        - A blank line between sections.
        - No markdown (#, **, tables), no emoji.
        - If the text is too short or unclear to arrange, return it as one \
        tidy paragraph.

        Put the arranged description in "answer". This is not a chat - \
        suggestedRoute and suggestedLabel are always null.
        """;

    // Heading words the AI adds on its own - never counted as invented.
    private static final Set<String> LAYOUT_WORDS = Set.of(
        "features", "feature", "specifications", "specification", "sizes", "size", "available", "uses", "care",
        "storage", "delivery", "overview", "details", "product", "about", "dimensions", "notes", "note", "grade",
        "length", "width", "thickness", "moisture", "origin", "treatment", "finish", "material", "type", "applications",
        "benefits", "pricing", "price", "quality", "maintenance", "installation", "steps", "instructions", "wood",
        "timber", "information", "options", "vipimo", "matumizi", "maelezo"
    );

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.]\\d+)?");
    private static final Pattern WORD = Pattern.compile("[\\p{L}]{4,}");

    private final JdbcTemplate jdbcTemplate;
    private final GroqClient groqClient;
    private final AnthropicClient anthropicClient;
    private final String chatProvider;
    // app.descriptions.ai-tidy-up - off means the website's built-in tidy-up
    // lays out every description on its own.
    private final boolean aiTidyUp;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "description-formatter");
        thread.setDaemon(true);
        return thread;
    });

    public DescriptionFormatter(
        JdbcTemplate jdbcTemplate,
        GroqClient groqClient,
        AnthropicClient anthropicClient,
        @Value("${app.ai.chat-provider}") String chatProvider,
        @Value("${app.descriptions.ai-tidy-up:true}") boolean aiTidyUp
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.groqClient = groqClient;
        this.anthropicClient = anthropicClient;
        this.chatProvider = chatProvider;
        this.aiTidyUp = aiTidyUp;
    }

    // Called after a product is saved - returns at once.
    public void formatLater(int productId) {
        worker.submit(() -> formatOne(productId, false));
    }

    // Anything whose tidied copy is missing or older than its description.
    @Scheduled(initialDelay = 45_000, fixedDelay = 300_000)
    public void sweep() {
        List<Integer> stale = jdbcTemplate.queryForList(
            "SELECT Product_ID FROM PRODUCTS WHERE Is_Deleted = FALSE AND " +
            "(Description_Display_Source IS NULL OR Description_Display_Source <> SHA2(IFNULL(Description, ''), 256)) " +
            "ORDER BY Product_ID LIMIT 10",
            Integer.class
        );
        for (Integer productId : stale) {
            worker.submit(() -> formatOne(productId, true));
        }
    }

    private void formatOne(int productId, boolean paced) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT Description, Description_Display_Source <=> SHA2(IFNULL(Description, ''), 256) AS Fresh " +
            "FROM PRODUCTS WHERE Product_ID = ?",
            productId
        );
        if (rows.isEmpty() || isTrue(rows.get(0).get("Fresh"))) {
            return;
        }
        String text = (String) rows.get(0).get("Description");
        if (!aiTidyUp || text == null || !needsArranging(text) || text.length() > AI_MAX_INPUT) {
            save(productId, text, null);
            return;
        }
        ChatAiClient client = "groq".equalsIgnoreCase(chatProvider) ? groqClient : anthropicClient;
        if (!client.isConfigured()) {
            return;
        }
        try {
            AiChatReply reply = client.chat(INSTRUCTIONS, List.of(), text);
            String arranged = cleanUp(reply.answer());
            String problem = unfaithful(text, arranged);
            if (problem != null) {
                // Not retried until the description changes - the website's
                // own tidy-up of the original is shown instead.
                log.warn("Tidied description for product {} rejected ({}); showing the original", productId, problem);
                save(productId, text, null);
            } else {
                save(productId, text, arranged);
                log.info("Tidied the description of product {} ({} -> {} characters)", productId, text.length(), arranged.length());
            }
        } catch (RuntimeException e) {
            // AI busy or unreachable: left stale, so the next sweep retries.
            log.info("Couldn't tidy product {}'s description yet ({}); will retry", productId, e.getMessage());
        } finally {
            if (paced) {
                pause();
            }
        }
    }

    // Only if the description is still the one that was arranged - a newer
    // save in the meantime queues its own run.
    private void save(int productId, String source, String arranged) {
        jdbcTemplate.update(
            "UPDATE PRODUCTS SET Description_Display = ?, Description_Display_Source = SHA2(IFNULL(Description, ''), 256) " +
            "WHERE Product_ID = ? AND Description <=> ?",
            arranged, productId, source
        );
    }

    static boolean needsArranging(String text) {
        return text.length() > FORMAT_FROM || text.strip().contains("\n");
    }

    static String cleanUp(String answer) {
        if (answer == null) {
            return "";
        }
        String text = answer.replace("\r\n", "\n").strip();
        text = text.replaceAll("(?m)^```[a-z]*\\s*$", "");
        // Markdown headings the model slipped in -> our "Heading:" form.
        text = text.replaceAll("(?m)^#{1,6}\\s*(.+?):?\\s*$", "$1:");
        text = text.replaceAll("(?m)^\\s*[*•]\\s+", "- ");
        text = text.replace("**", "");
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.strip();
    }

    // Why the arranged text can't be trusted, or null if it can.
    static String unfaithful(String original, String arranged) {
        if (arranged.isBlank()) {
            return "empty";
        }
        double ratio = (double) arranged.length() / original.length();
        if (ratio < 0.6 || ratio > 2.2) {
            return String.format(Locale.ROOT, "length changed x%.2f", ratio);
        }
        Set<String> before = numbers(original);
        Set<String> after = numbers(arranged);
        if (!before.equals(after)) {
            Set<String> invented = new HashSet<>(after);
            invented.removeAll(before);
            Set<String> dropped = new HashSet<>(before);
            dropped.removeAll(after);
            return "numbers differ - added " + invented + ", dropped " + dropped;
        }
        Set<String> originalWords = words(original);
        Set<String> arrangedWords = words(arranged);
        if (!originalWords.isEmpty()) {
            long kept = originalWords.stream().filter(arrangedWords::contains).count();
            if (kept < originalWords.size() * 0.75) {
                return "only " + kept + " of " + originalWords.size() + " words kept";
            }
        }
        long added = arrangedWords.stream().filter(w -> !originalWords.contains(w) && !LAYOUT_WORDS.contains(w)).count();
        if (!arrangedWords.isEmpty() && added > Math.max(4, arrangedWords.size() * 0.25)) {
            return added + " new words";
        }
        return null;
    }

    // "3,000" and "3000" are the same number.
    private static Set<String> numbers(String text) {
        Set<String> found = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text.replaceAll("(?<=\\d),(?=\\d{3})", ""));
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static Set<String> words(String text) {
        Set<String> found = new HashSet<>();
        Matcher matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static boolean isTrue(Object value) {
        return value instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(value);
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_BETWEEN_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
