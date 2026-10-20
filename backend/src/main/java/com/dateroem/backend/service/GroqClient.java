package com.dateroem.backend.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.dateroem.backend.dto.AiChatReply;
import com.dateroem.backend.dto.ChatTurn;
import com.dateroem.backend.util.FlakyNetworkRetry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Groq Cloud's OpenAI-compatible chat completions API - "before i pay i
 * want to ensure the chat bot is ok and answers ok then i can proceed to
 * the paying ones". Groq's free tier needs no billing/card on file, so
 * this is what AiChatService talks to while app.ai.chat-provider=groq;
 * switching to app.ai.chat-provider=anthropic (AnthropicClient) once
 * that account is funded is a property change, not a code change.
 *
 * Only the chat feature uses this. The handwriting-scan feature
 * (AnthropicClient.scan()) is untouched - it's vision-specific and
 * wasn't part of this request.
 */
@Service
public class GroqClient implements ChatAiClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    // Same response-format contract AnthropicClient asks Claude for, so
    // AiChatService's caller-side handling (suggestedRoute validation,
    // AiChatReply shape) doesn't need to know or care which provider
    // actually answered.
    private static final String CHAT_INSTRUCTIONS = """

        Respond with ONLY a JSON object, no other text, no markdown code \
        fences, with exactly these keys:
        - answer: your reply, laid out exactly as the instructions above say
        - suggestedRoute: if your answer points the user to a specific \
          place in this app, the EXACT route string from the "Available \
          shortcuts" list above (copy it exactly, never invent or guess \
          one) - else null
        - suggestedLabel: a short button label for that shortcut (e.g. \
          "Go to STK Push") - else null
        """;

    // gpt-oss "thinks" before answering, and those hidden reasoning tokens
    // count against the reply budget - at the default effort a long answer
    // could be cut off mid-JSON, which is part of why replies came out "not
    // quite there". Low effort is plenty for a shop assistant and leaves
    // the budget (and the per-minute token allowance) for the answer.
    private static final int MAX_COMPLETION_TOKENS = 1500;

    // All models busy at once: waiting this long for the soonest one to free
    // up still fits inside the website proxy's 45s timeout.
    private static final Duration MAX_WAIT_FOR_FREE_MODEL = Duration.ofSeconds(12);

    private final String apiKey;
    // "remembering that there is a limit to the number of prompts per minute
    // from my ai provider" - Groq's free tier allows each model 8,000 tokens
    // a minute, and every model has its own separate allowance. When the
    // first model is at its limit the next one answers, so a busy minute
    // still gets a real reply instead of a canned fallback.
    private final List<String> chatModels;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqClient(
        @Value("${app.groq.api-key}") String apiKey,
        @Value("${app.groq.chat-model}") String chatModel,
        @Value("${app.groq.fallback-chat-models:}") String fallbackChatModels,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        Set<String> models = new LinkedHashSet<>();
        models.add(chatModel.trim());
        for (String model : fallbackChatModels.split(",")) {
            if (!model.isBlank()) {
                models.add(model.trim());
            }
        }
        this.chatModels = List.copyOf(models);
        this.objectMapper = objectMapper;
        // "Connection reset" mid-request against Groq's endpoint
        // specifically (Anthropic's own endpoint was fine) with the JDK
        // client's default HTTP/2-with-ALPN negotiation - a known failure
        // mode when something on the network path (security software,
        // a proxy) doesn't handle h2 cleanly. Forcing HTTP/1.1 avoids that
        // negotiation entirely.
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
            .baseUrl("https://api.groq.com/openai/v1")
            .requestFactory(requestFactory)
            // "why not provide a valid answer?" - traced to this network
            // occasionally landing on a dead/broken route to Groq (a
            // connect timeout, or a garbled response like "header parser
            // received no bytes"), which silently pushed the website onto
            // help.py's dumb keyword bot instead. A chat call has no side
            // effects, so retrying on any I/O error here is safe.
            .requestInterceptor(FlakyNetworkRetry.forReads())
            .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // OpenAI-compatible shape: system prompt is just another message with
    // role "system", not a separate top-level field like Anthropic's API.
    @Override
    public AiChatReply chat(String systemPrompt, List<ChatTurn> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt + CHAT_INSTRUCTIONS));
        for (ChatTurn turn : history) {
            // Past answers are stored as plain text; shown back to the model
            // that way, it copied them and answered in plain text too, which
            // JSON mode then rejected outright. Replaying them in the same
            // JSON shape it must answer in keeps it on format.
            String content = "assistant".equals(turn.role()) ? asJsonReply(turn.content()) : turn.content();
            messages.add(Map.of("role", turn.role(), "content", content));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Attempt attempt = tryEachModel(messages);
        if (attempt.reply() == null && attempt.busyFor().compareTo(MAX_WAIT_FOR_FREE_MODEL) <= 0) {
            sleep(attempt.busyFor());
            attempt = tryEachModel(messages);
        }
        if (attempt.reply() != null) {
            return attempt.reply();
        }
        throw new AiBusyException(attempt.busyFor());
    }

    // Either a model answered (reply), or every model is at its limit and
    // busyFor says how soon the first one frees up.
    private record Attempt(AiChatReply reply, Duration busyFor) {}

    private Attempt tryEachModel(List<Map<String, Object>> messages) {
        Duration soonest = null;
        for (String model : chatModels) {
            try {
                return new Attempt(parseChatReply(extractText(complete(model, messages))), null);
            } catch (HttpClientErrorException.BadRequest e) {
                // JSON mode refuses a reply that isn't valid JSON - but the
                // reply itself comes back in "failed_generation", and it's
                // still the model's real answer, just not wrapped.
                String failedGeneration = failedGeneration(e);
                if (failedGeneration == null) {
                    throw e;
                }
                log.warn("Groq {} answered outside JSON mode; using its text as-is", model);
                return new Attempt(parseChatReply(failedGeneration), null);
            } catch (HttpClientErrorException.TooManyRequests e) {
                Duration wait = retryAfter(e);
                log.warn("Groq model {} is at its per-minute limit (free again in {}s)", model, wait.toSeconds());
                if (soonest == null || wait.compareTo(soonest) < 0) {
                    soonest = wait;
                }
            }
        }
        return new Attempt(null, soonest);
    }

    private Map<String, Object> complete(String model, List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_completion_tokens", MAX_COMPLETION_TOKENS);
        body.put("messages", messages);
        // JSON mode - the reply is always a parseable object, instead of
        // sometimes prose with the JSON somewhere inside it.
        body.put("response_format", Map.of("type", "json_object"));
        if (model.startsWith("openai/gpt-oss")) {
            body.put("reasoning_effort", "low");
        }

        ResponseEntity<Map<String, Object>> response = restClient.post()
            .uri("/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

        logUsage(model, response);
        return response.getBody();
    }

    // One line per answer: which model, how many tokens, and how much of
    // this minute's allowance is left - the numbers to watch against the
    // free tier's per-minute limit.
    @SuppressWarnings("unchecked")
    private void logUsage(String model, ResponseEntity<Map<String, Object>> response) {
        Map<String, Object> usage = response.getBody() == null ? null : (Map<String, Object>) response.getBody().get("usage");
        log.info(
            "Groq {} answered: {} prompt + {} completion tokens, {} tokens left this minute, {} requests left today",
            model,
            usage == null ? "?" : usage.get("prompt_tokens"),
            usage == null ? "?" : usage.get("completion_tokens"),
            response.getHeaders().getFirst("x-ratelimit-remaining-tokens"),
            response.getHeaders().getFirst("x-ratelimit-remaining-requests")
        );
    }

    @SuppressWarnings("unchecked")
    private String failedGeneration(HttpClientErrorException e) {
        try {
            Map<String, Object> body = objectMapper.readValue(e.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            if (error == null || !"json_validate_failed".equals(error.get("code"))) {
                return null;
            }
            Object text = error.get("failed_generation");
            return text instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception notJson) {
            return null;
        }
    }

    private String asJsonReply(String answer) {
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("answer", answer);
        reply.put("suggestedRoute", null);
        reply.put("suggestedLabel", null);
        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception e) {
            return answer;
        }
    }

    private static Duration retryAfter(HttpClientErrorException e) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("retry-after");
        try {
            return Duration.ofMillis(Math.max(1000, Math.round(Double.parseDouble(header) * 1000)));
        } catch (RuntimeException notANumber) {
            return Duration.ofSeconds(20);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        List<Map<String, Object>> choices = response == null ? null : (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq's response had no choices: " + response);
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private AiChatReply parseChatReply(String text) {
        String json = stripMarkdownFence(text.trim());
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return new AiChatReply(
                withStrayParts(parsed),
                parsed.get("suggestedRoute") instanceof String route ? route : null,
                parsed.get("suggestedLabel") instanceof String label ? label : null
            );
        } catch (Exception e) {
            // Same reasoning as AnthropicClient.parseChatReply - a reply
            // that isn't clean JSON is still usable as plain text.
            return new AiChatReply(text, null, null);
        }
    }

    // Seen in testing: "how do I place an order?" came back as just "here's
    // how you can place an order:" - the model had put the steps under a key
    // of its own ("steps") next to "answer". Any such extra list or text is
    // folded back into the answer instead of being dropped.
    private static String withStrayParts(Map<String, Object> parsed) {
        StringBuilder answer = new StringBuilder(parsed.get("answer") instanceof String a ? a : "");
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            if (Set.of("answer", "suggestedRoute", "suggestedLabel").contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof List<?> items) {
                int n = 1;
                for (Object item : items) {
                    if (item instanceof String text && !text.isBlank()) {
                        answer.append("\n").append(n++).append(". ").append(text.strip());
                    }
                }
            } else if (entry.getValue() instanceof String text && text.strip().length() > 20) {
                answer.append("\n").append(text.strip());
            }
        }
        return answer.toString();
    }

    // Open-weight models on Groq wrap JSON in ```json ... ``` fences even
    // more often than Claude does - same strip-and-retry as AnthropicClient.
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
}
