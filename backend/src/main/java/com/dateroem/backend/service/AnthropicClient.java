package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.dateroem.backend.controller.DeliveryController.ProductionDescriptionRequest;
import com.dateroem.backend.dto.AiChatReply;
import com.dateroem.backend.dto.ChatTurn;
import com.dateroem.backend.util.FlakyNetworkRetry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anthropic (Claude) client for the handwriting-scan feature - "the ai
 * will pick a picture ... and it will scan that picture for data and
 * fill them ... one can have a picture of the BULK_TRIP_PRODUCTION_DESCRIPTION
 * for the product description ... it should scan this in the product
 * description add all this values as they are and in the total body feet
 * sections it can add the ... total". Replaces the original OpenAI-backed
 * client - "use the claude api since im in the pro mode for now for
 * testing then later i can use another api key from another model" - the
 * OpenAI project this was first tried against only had access to a
 * text-only legacy model (no vision), so this switched providers rather
 * than waiting on that account's billing setup.
 *
 * Same isConfigured()-gated shape as MpesaClient/the original
 * OpenAiClient: blank API key until filled in, callers fall back
 * gracefully. Never touches the database itself - DeliveryController
 * returns the extracted values straight back to the admin to review in
 * the existing "Add Entry" form before anything is saved.
 */
@Service
public class AnthropicClient implements ChatAiClient {

    private static final String PROMPT = """
        You are extracting timber/wood measurement data from a photo of a \
        handwritten note. These notes are usually a list of dimension \
        entries like "6*6*4 5*6*9 6*5*8" followed by a final total number \
        like "1200".

        Extract exactly these fields and respond with ONLY a JSON object, \
        no other text, no markdown code fences:
        - productType: the type of wood/product if mentioned, else null
        - productDescription: the raw dimension entries exactly as \
          written, space-separated (e.g. "6*6*4 5*6*9 6*5*8")
        - productMeasurement: the unit if inferable (usually "board \
          feet"), else null
        - totalBodyFeet: the final total number if explicitly written on \
          the note, else null - do not guess or compute one yourself
        - productTotal: same as totalBodyFeet unless a separate total is \
          clearly shown, else null
        """;

    // "make the inbuild ai more stronger based on current ai feature
    // standard" - the image-scan task is narrow (structured extraction),
    // so it stays on the fast/cheap model; a real back-and-forth
    // conversation benefits from a stronger one, so chat gets its own
    // separately-configured model rather than sharing scanModel.
    private static final String CHAT_INSTRUCTIONS = """

        Respond with ONLY a JSON object, no other text, no markdown code \
        fences, with exactly these keys:
        - answer: your reply, in plain conversational text
        - suggestedRoute: if your answer points the user to a specific \
          place in this app, the EXACT route string from the "Available \
          shortcuts" list above (copy it exactly, never invent or guess \
          one) - else null
        - suggestedLabel: a short button label for that shortcut (e.g. \
          "Go to STK Push") - else null
        """;

    private final String apiKey;
    private final String scanModel;
    private final String chatModel;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AnthropicClient(
        @Value("${app.anthropic.api-key}") String apiKey,
        @Value("${app.anthropic.model}") String scanModel,
        @Value("${app.anthropic.chat-model}") String chatModel,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.scanModel = scanModel;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        // RestClient.create() alone has no timeout at all - a stalled TCP
        // connection (VPN/proxy black-holing the request, DNS hiccup, ...)
        // would hang the request thread forever instead of failing, which
        // is exactly what surfaced as "the chat just hangs, then eventually
        // falls back to the canned answer" - fail fast and let the caller's
        // own error handling (falls back to the free lookup bot on the
        // website) take over instead.
        // Forcing HTTP/1.1 here too, same reasoning as GroqClient - avoids
        // the JDK client's HTTP/2 negotiation entirely rather than relying
        // on it happening to work on every network this runs on.
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            // Short on purpose now that connect failures are retried up to
            // 5 times below (see FlakyNetworkRetry) - 15s here would let a
            // fully-dead route eat 75s total before giving up, well past
            // the website proxy's own 45s timeout.
            .connectTimeout(Duration.ofSeconds(6))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder()
            .baseUrl("https://api.anthropic.com")
            .requestFactory(requestFactory)
            // Same fix as GroqClient - "why not provide a valid answer?"
            // traced to this network occasionally hitting a dead/broken
            // route; a chat/scan call has no side effects, so retrying on
            // any I/O error is safe.
            .requestInterceptor(FlakyNetworkRetry.forReads())
            .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // imageDataUrl must be a full data: URL ("data:image/jpeg;base64,...")
    // - split into Anthropic's separate media_type/data fields here so
    // the wire format sent by the desktop app (and the BFF layers between
    // it and here) stays provider-agnostic, same shape either way.
    public ProductionDescriptionRequest scan(String imageDataUrl) {
        String[] mediaTypeAndData = splitDataUrl(imageDataUrl);

        Map<String, Object> body = Map.of(
            "model", scanModel,
            "max_tokens", 1024,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of(
                        "type", "image",
                        "source", Map.of(
                            "type", "base64",
                            "media_type", mediaTypeAndData[0],
                            "data", mediaTypeAndData[1]
                        )
                    ),
                    Map.of("type", "text", "text", PROMPT)
                )
            ))
        );

        Map<String, Object> response = restClient.post()
            .uri("/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        return parseResult(extractText(response));
    }

    // "implement the chat bot ... to allow it to answer questions still
    // role based but still the normal question and answer ... e.g. where
    // can i do an mpesa stk push if you are an admin it will answer you
    // go to this then this or provide a short cut to that section as an
    // alternative". systemPrompt (built by AiChatService) already carries
    // the business description, the user's role, and the list of routes
    // this specific user is allowed to be pointed at - this method only
    // adds the fixed response-format instructions and the running
    // conversation.
    @Override
    public AiChatReply chat(String systemPrompt, List<ChatTurn> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatTurn turn : history) {
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of(
            "model", chatModel,
            "max_tokens", 1024,
            "system", systemPrompt + CHAT_INSTRUCTIONS,
            "messages", messages
        );

        Map<String, Object> response = restClient.post()
            .uri("/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        return parseChatReply(extractText(response));
    }

    private AiChatReply parseChatReply(String text) {
        String json = stripMarkdownFence(text.trim());
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return new AiChatReply(
                (String) parsed.get("answer"),
                (String) parsed.get("suggestedRoute"),
                (String) parsed.get("suggestedLabel")
            );
        } catch (Exception e) {
            // A chat reply that didn't come back as clean JSON is still a
            // usable answer as plain text - fall back to that rather than
            // failing the whole request over a formatting hiccup (unlike
            // scan(), where a malformed response has nothing safe to fall
            // back to).
            return new AiChatReply(text, null, null);
        }
    }

    private String[] splitDataUrl(String imageDataUrl) {
        // "data:image/png;base64,iVBORw0..." -> ["image/png", "iVBORw0..."]
        int commaIndex = imageDataUrl.indexOf(',');
        if (!imageDataUrl.startsWith("data:") || commaIndex < 0) {
            throw new IllegalArgumentException("imageDataUrl must be a data: URL");
        }
        String header = imageDataUrl.substring(5, commaIndex);
        String mediaType = header.split(";")[0];
        String data = imageDataUrl.substring(commaIndex + 1);
        return new String[] { mediaType, data };
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Claude's response had no content: " + response);
        }
        return (String) content.get(0).get("text");
    }

    private ProductionDescriptionRequest parseResult(String text) {
        String json = stripMarkdownFence(text.trim());
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return new ProductionDescriptionRequest(
                (String) parsed.get("productType"),
                (String) parsed.get("productDescription"),
                (String) parsed.get("productMeasurement"),
                toBigDecimal(parsed.get("totalBodyFeet")),
                toBigDecimal(parsed.get("productTotal")),
                null
            );
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't parse Claude's response into the expected fields: " + text, e);
        }
    }

    // Claude sometimes wraps JSON in ```json ... ``` fences despite being
    // told not to - strip them rather than failing to parse.
    private String stripMarkdownFence(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(value.toString());
    }
}
