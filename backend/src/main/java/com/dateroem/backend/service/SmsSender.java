package com.dateroem.backend.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends the text messages that confirm a customer's phone number ("add an
 * sms feature to push a code to the phone number they placed so that its
 * verified") - PhoneVerificationService is the only caller.
 *
 * app.sms.provider:
 * - off (the default): nothing is sent and phone numbers aren't checked -
 *   sign-up and checkout work exactly as before.
 * - africastalking: Africa's Talking's SMS API, with app.sms.username and
 *   app.sms.api-key from the Africa's Talking account. The username
 *   "sandbox" uses their sandbox (messages appear in their simulator, no
 *   real SMS); any other username sends real messages.
 * - test: for checking the feature on this machine only - each message is
 *   added to the file app.sms.test-outbox instead of being sent.
 *
 * The code inside a message is never written to the log.
 */
@Service
public class SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);

    private static final String LIVE_URL = "https://api.africastalking.com/version1/messaging";
    private static final String SANDBOX_URL = "https://api.sandbox.africastalking.com/version1/messaging";

    private final String provider;
    private final String username;
    private final String apiKey;
    private final String senderId;
    private final Path testOutbox;
    // Normally empty (Africa's Talking's own address); a local stand-in when
    // checking how failures are handled, without sending real texts.
    private final String messagingUrl;
    private final RestClient restClient;

    public SmsSender(@Value("${app.sms.provider:off}") String provider,
                     @Value("${app.sms.username:}") String username,
                     @Value("${app.sms.api-key:}") String apiKey,
                     @Value("${app.sms.sender-id:}") String senderId,
                     @Value("${app.sms.test-outbox:sms-test-outbox.txt}") String testOutbox,
                     @Value("${app.sms.africastalking-url:}") String messagingUrl) {
        this.provider = provider == null ? "off" : provider.trim().toLowerCase();
        this.username = username == null ? "" : username.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.senderId = senderId == null ? "" : senderId.trim();
        this.testOutbox = Path.of(testOutbox);
        this.messagingUrl = messagingUrl == null || messagingUrl.isBlank()
            ? ("sandbox".equalsIgnoreCase(this.username) ? SANDBOX_URL : LIVE_URL) : messagingUrl.trim();
        // The website waits 10 seconds for the backend; a slow SMS gateway
        // must answer (or fail) well inside that.
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        if ("africastalking".equals(this.provider) && (this.username.isEmpty() || this.apiKey.isEmpty())) {
            log.warn("app.sms.provider is africastalking but the username or API key is empty - phone numbers are not checked until both are set");
        } else if (isEnabled()) {
            log.info("Phone numbers are confirmed by SMS ({})", "test".equals(this.provider) ? "test outbox" : "Africa's Talking"
                + ("sandbox".equalsIgnoreCase(this.username) ? " sandbox" : ""));
        }
    }

    /** Whether phone numbers must be confirmed by a code (a working SMS setup exists). */
    public boolean isEnabled() {
        return "test".equals(provider)
            || ("africastalking".equals(provider) && !username.isEmpty() && !apiKey.isEmpty());
    }

    public boolean isTestMode() {
        return "test".equals(provider);
    }

    public enum Outcome { SENT, UNAVAILABLE }

    // When Africa's Talking can't send (the balance ran out, their service
    // is down), codes pause for this long and customers carry on as before
    // SMS codes - a customer is never kept from signing up or paying
    // because the SMS wallet needs topping up.
    private static final long PAUSE_MILLIS = 10 * 60 * 1000;
    private volatile long pausedUntil = 0;

    /** False for a while after Africa's Talking couldn't send. */
    public boolean isAvailable() {
        return System.currentTimeMillis() >= pausedUntil;
    }

    /**
     * Sends one message to a 2547XXXXXXXX / 2541XXXXXXXX number: SENT, or
     * UNAVAILABLE when the SMS service couldn't take it (codes then pause,
     * see isAvailable). A number that can't receive texts at all is a 400.
     */
    public Outcome send(String phone254, String message) {
        if ("test".equals(provider)) {
            writeToTestOutbox(phone254, message);
            return Outcome.SENT;
        }
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Text messages are not set up yet.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", "+" + phone254);
        form.add("message", message);
        if (!senderId.isEmpty()) {
            form.add("from", senderId);
        }
        Map<String, Object> body;
        try {
            body = restClient.post()
                .uri(messagingUrl)
                .header("apiKey", apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            return pause(phone254, e.getClass().getSimpleName());
        }
        // {"SMSMessageData": {"Message": "...", "Recipients": [{"statusCode": 101, "status": "Success", ...}]}}
        // 100 processed, 101 sent, 102 queued; 403 invalid number and 404 a
        // number type that can't get texts are the customer's to fix; anything
        // else (405 insufficient balance, gateway errors ...) is on our side.
        Object data = body == null ? null : body.get("SMSMessageData");
        Object recipients = data instanceof Map<?, ?> m ? m.get("Recipients") : null;
        Object first = recipients instanceof List<?> list && !list.isEmpty() ? list.get(0) : null;
        Object code = first instanceof Map<?, ?> r ? r.get("statusCode") : null;
        int statusCode = code instanceof Number n ? n.intValue() : -1;
        if (statusCode >= 100 && statusCode <= 102) {
            return Outcome.SENT;
        }
        Object status = first instanceof Map<?, ?> r ? r.get("status") : (data instanceof Map<?, ?> m ? m.get("Message") : null);
        if (statusCode == 403 || statusCode == 404) {
            log.info("Africa's Talking: {} can't receive texts ({} {})", masked(phone254), statusCode, status);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "That number can't receive text messages. Please check it, or use another number.");
        }
        return pause(phone254, statusCode + " " + status);
    }

    private Outcome pause(String phone254, String reason) {
        pausedUntil = System.currentTimeMillis() + PAUSE_MILLIS;
        log.warn("Africa's Talking couldn't send an SMS to {} ({}) - phone codes paused for 10 minutes; "
            + "if this says InsufficientBalance, top up the Africa's Talking wallet", masked(phone254), reason);
        return Outcome.UNAVAILABLE;
    }

    private void writeToTestOutbox(String phone254, String message) {
        try {
            if (!Files.exists(testOutbox)) {
                Files.createFile(testOutbox);
                try {
                    Files.setPosixFilePermissions(testOutbox, PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException ignored) {
                    // Not a POSIX file system: the file keeps its default permissions.
                }
            }
            Files.writeString(testOutbox, LocalDateTime.now() + "\t" + phone254 + "\t" + message + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            log.info("SMS to {} written to the test outbox (not sent)", masked(phone254));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The test outbox can't be written: " + e.getMessage());
        }
    }

    /** 2547XXXX1234 -> 07XX XXX 234: enough for a person to recognise their number. */
    public static String masked(String phone254) {
        if (phone254 == null || phone254.length() != 12) {
            return "a phone number";
        }
        return "0" + phone254.charAt(3) + "XX XXX " + phone254.substring(9);
    }
}
