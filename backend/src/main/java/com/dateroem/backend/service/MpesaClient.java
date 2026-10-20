package com.dateroem.backend.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Safaricom Daraja client, both directions:
 *  - STK push (C2B/"Lipa na M-Pesa Online"): pulls money FROM a customer -
 *    product payments on the website, bulk-goods payments here.
 *  - B2C ("Send Money"): pushes money OUT to a partner - business owner
 *    correction: "expenses are payments done to the other partners ... i
 *    dont think its an stk push but rather a send money option."
 * "create the pathways correctly for testing later we will then fill the
 * correct credentials so now we do the sandbox testing then later we go
 * production." base-url defaults to the sandbox host; every credential
 * defaults blank until filled in (application.properties). isConfigured()/
 * isB2CConfigured() gate every caller so an unconfigured environment
 * behaves exactly as before (bare pending FINANCIAL_TRANSACTION row +
 * manual confirm), never throwing.
 */
@Service
public class MpesaClient {

    private static final Logger log = LoggerFactory.getLogger(MpesaClient.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();
    // Daraja's "the customer hasn't answered the prompt yet" signal on the
    // status-query endpoint - NOT a failure, treating it as one would mark
    // every order FAILED the instant the prompt is still sitting on the
    // phone (confirmed live: a real order was marked FAILED by this exact
    // path seconds before the prompt actually reached the phone). It
    // arrives two different ways depending on how fast the query lands
    // after the push: as an HTTP 500 with this errorCode in the body, or -
    // just as often, since the whole point of querying early is to catch
    // callback URL gaps - as a normal 200 OK with ResultCode "4999" in the
    // body. Both mean the same thing and both are handled below.
    private static final String STILL_PROCESSING_ERROR = "500.001.1001";
    private static final String STILL_PROCESSING_RESULT_CODE = "4999";

    private final String consumerKey;
    private final String consumerSecret;
    private final String shortcode;
    private final String passkey;
    private final String callbackUrl;
    private final boolean sandbox;
    private final String b2cInitiatorName;
    private final String b2cInitiatorPassword;
    private final String b2cShortcode;
    private final String b2cCertPath;
    private final String b2cResultUrl;
    private final String b2cQueueTimeoutUrl;
    // Paybill or till (app.mpesa.transaction-type / app.mpesa.party-b).
    private final String transactionType;
    private final String partyB;
    private final RestClient restClient;

    public MpesaClient(
        @Value("${app.mpesa.base-url}") String baseUrl,
        @Value("${app.mpesa.consumer-key}") String consumerKey,
        @Value("${app.mpesa.consumer-secret}") String consumerSecret,
        @Value("${app.mpesa.shortcode}") String shortcode,
        @Value("${app.mpesa.passkey}") String passkey,
        @Value("${app.mpesa.callback-url}") String callbackUrl,
        @Value("${app.mpesa.b2c-initiator-name}") String b2cInitiatorName,
        @Value("${app.mpesa.b2c-initiator-password}") String b2cInitiatorPassword,
        @Value("${app.mpesa.b2c-shortcode}") String b2cShortcode,
        @Value("${app.mpesa.b2c-cert-path}") String b2cCertPath,
        @Value("${app.mpesa.b2c-result-url}") String b2cResultUrl,
        @Value("${app.mpesa.b2c-queue-timeout-url}") String b2cQueueTimeoutUrl,
        @Value("${app.mpesa.transaction-type:CustomerPayBillOnline}") String transactionType,
        @Value("${app.mpesa.party-b:}") String partyB
    ) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.callbackUrl = callbackUrl;
        this.sandbox = baseUrl != null && baseUrl.contains("sandbox");
        this.b2cInitiatorName = b2cInitiatorName;
        this.b2cInitiatorPassword = b2cInitiatorPassword;
        this.b2cShortcode = b2cShortcode;
        this.b2cCertPath = b2cCertPath;
        this.b2cResultUrl = b2cResultUrl;
        this.b2cQueueTimeoutUrl = b2cQueueTimeoutUrl;
        this.transactionType = notBlank(transactionType) ? transactionType.trim() : "CustomerPayBillOnline";
        // Empty means the paybill case: customers pay the shortcode itself.
        this.partyB = notBlank(partyB) ? partyB.trim() : shortcode;
        // "still it does now works" - the actual cause: intermittent
        // "Connection reset"/"No route to host" reaching Safaricom's
        // sandbox, same root issue already diagnosed and fixed for
        // AnthropicClient/GroqClient (the JDK HttpClient's default HTTP/2
        // negotiation getting mangled on this network) - this client never
        // got the same fix. Forcing HTTP/1.1 plus real timeouts instead of
        // RestClient.create()'s unconfigured defaults (which had no
        // timeout at all, so a stalled connection could hang forever
        // rather than failing fast).
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(4))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .requestInterceptor(MpesaClient::retryDeadAddresses)
            .build();
    }

    // Safaricom's sandbox name resolves to several addresses and one of them
    // is unreachable from this network: whenever the JVM lands on it every
    // call just times out (curl copes because it tries the next address; the
    // JDK client doesn't). Each retry re-resolves the name (DNS caching is
    // off, see BackendApplication) and so usually gets a live address. A
    // connect failure means nothing was sent, so even a payout is safe to
    // retry; other I/O errors are only retried for reads (GET).
    private static org.springframework.http.client.ClientHttpResponse retryDeadAddresses(
        org.springframework.http.HttpRequest request, byte[] body,
        org.springframework.http.client.ClientHttpRequestExecution execution
    ) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return execution.execute(request, body);
            } catch (IOException e) {
                if (!isConnectFailure(e) && request.getMethod() != HttpMethod.GET) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }

    private static boolean isConnectFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
                return true;
            }
        }
        return false;
    }

    public boolean isConfigured() {
        return notBlank(consumerKey) && notBlank(consumerSecret) && notBlank(shortcode)
            && notBlank(passkey) && notBlank(callbackUrl);
    }

    // Needs the OAuth pair (same as STK) plus the B2C-specific initiator
    // credentials and Safaricom's public certificate file to encrypt the
    // security credential with - none of that exists until it's filled in.
    // The Daraja sandbox only pays out to Safaricom's own test number - any
    // other phone fails with "Credit Party customer type can't be supported".
    public boolean isSandbox() {
        return sandbox;
    }

    public boolean isB2CConfigured() {
        return notBlank(consumerKey) && notBlank(consumerSecret) && notBlank(b2cInitiatorName)
            && notBlank(b2cInitiatorPassword) && notBlank(b2cShortcode) && notBlank(b2cResultUrl)
            && notBlank(b2cQueueTimeoutUrl) && notBlank(b2cCertPath) && Files.isRegularFile(Path.of(b2cCertPath));
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public record StkPushResult(String merchantRequestId, String checkoutRequestId) {}

    /**
     * Every Daraja failure reaches callers as this, carrying a reason
     * that's actually fit to show someone. Previously callers only logged
     * {@code e.getMessage()} of whatever was thrown, which for a Daraja
     * rejection is the bare HTTP status - the useful part (errorCode /
     * errorMessage in the response body, e.g. "400.002.02 - Bad Request -
     * Invalid CallBackURL") was discarded, which is exactly why a failing
     * push was so hard to diagnose from the logs.
     */
    public static class MpesaException extends RuntimeException {
        private final String errorCode;

        MpesaException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }

    /**
     * Initiates a real STK push prompt. Throws {@link MpesaException} on
     * any failure (bad credentials, network, Safaricom-side rejection) -
     * callers must catch this and fall back gracefully rather than failing
     * the whole request, since sandbox credentials won't exist until
     * they're filled in.
     */
    public StkPushResult initiateStkPush(String phoneNumber, BigDecimal amount, String accountReference, String transactionDesc) {
        String accessToken = fetchAccessToken();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
      
        String toEncode = shortcode + passkey + timestamp;
        String password = Base64.getEncoder().encodeToString(toEncode.getBytes(StandardCharsets.UTF_8));

        String normalizedPhone = normalizePhone(phoneNumber);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("TransactionType", transactionType);
        // Daraja's STK push API rejects decimals - whole shillings only.
        body.put("Amount", amount.setScale(0, RoundingMode.DOWN).toPlainString());
        body.put("PartyA", normalizedPhone);
        body.put("PartyB", partyB);
        body.put("PhoneNumber", normalizedPhone);
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", accountReference);
        body.put("TransactionDesc", transactionDesc);

        Map<String, Object> response;
        try {
            response = restClient.post()
                .uri("/mpesa/stkpush/v1/processrequest")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException e) {
            throw darajaError("STK push rejected", e);
        } catch (Exception e) {
            throw new MpesaException(null, "Couldn't reach Safaricom to send the payment prompt: " + e.getMessage(), e);
        }

        log.info("Daraja STK push response for phone={}: {}", normalizedPhone, response);

        // Daraja does not only fail with an HTTP error status - it also
        // answers 200 OK carrying a non-zero ResponseCode (and then no
        // CheckoutRequestID at all). That used to be read as success: the
        // order got a PENDING payment row whose Provider_Ref was null, so
        // no callback could ever match it and the website told the customer
        // to check a phone that never rang.
        if (response == null) {
            throw new MpesaException(null, "Safaricom returned an empty response to the payment prompt", null);
        }
        String responseCode = asText(response.get("ResponseCode"));
        String checkoutRequestId = asText(response.get("CheckoutRequestID"));
        if (!"0".equals(responseCode) || checkoutRequestId == null) {
            String description = asText(response.get("ResponseDescription"));
            throw new MpesaException(
                responseCode,
                "Safaricom didn't accept the payment prompt" +
                    (description == null ? "" : " - " + description)
            , null);
        }

        return new StkPushResult(asText(response.get("MerchantRequestID")), checkoutRequestId);
    }

    /** How a checkout request stands right now, per Safaricom. */
    public enum StkStatus { SUCCESS, FAILED, PENDING }

    public record StkStatusResult(StkStatus status, String resultCode, String description) {}

    /**
     * Asks Safaricom directly what became of a checkout request, rather
     * than waiting to be told. This is the reason the website can confirm
     * a payment at all on this setup: the callback URL has to be a public
     * HTTPS address, which in development is an ngrok tunnel that is
     * frequently not running - and when nothing posts the callback, a paid
     * order stays PENDING forever and the confirmation page just spins.
     * Polling this endpoint closes that hole.
     *
     * Never throws: anything ambiguous (network trouble, an unrecognised
     * response, the customer simply not having answered yet) comes back as
     * PENDING, so a caller polling this can only ever leave an order alone,
     * never wrongly fail it.
     */
    public StkStatusResult queryStkStatus(String checkoutRequestId) {
        try {
            String accessToken = fetchAccessToken();
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8)
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("BusinessShortCode", shortcode);
            body.put("Password", password);
            body.put("Timestamp", timestamp);
            body.put("CheckoutRequestID", checkoutRequestId);

            Map<String, Object> response = restClient.post()
                .uri("/mpesa/stkpushquery/v1/query")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            String resultCode = response == null ? null : asText(response.get("ResultCode"));
            String resultDesc = response == null ? null : asText(response.get("ResultDesc"));
            if (resultCode == null || STILL_PROCESSING_RESULT_CODE.equals(resultCode)) {
                return new StkStatusResult(StkStatus.PENDING, resultCode, "Waiting for the customer to enter their PIN");
            }
            // 0 is the only success. Everything else still real is
            // settled-and-failed: 1032 cancelled on the handset, 1037 the
            // prompt timed out unanswered, 1 insufficient balance, 2001
            // wrong PIN.
            return new StkStatusResult(
                "0".equals(resultCode) ? StkStatus.SUCCESS : StkStatus.FAILED,
                resultCode,
                resultDesc
            );
        } catch (RestClientResponseException e) {
            MpesaException parsed = darajaError("STK status query failed", e);
            if (STILL_PROCESSING_ERROR.equals(parsed.errorCode())) {
                return new StkStatusResult(StkStatus.PENDING, parsed.errorCode(), "Waiting for the customer to enter their PIN");
            }
            log.warn("Daraja STK status query for CheckoutRequestID={}: {}", checkoutRequestId, parsed.getMessage());
            return new StkStatusResult(StkStatus.PENDING, parsed.errorCode(), parsed.getMessage());
        } catch (Exception e) {
            log.warn("Couldn't query Daraja for CheckoutRequestID={}: {}", checkoutRequestId, e.getMessage());
            return new StkStatusResult(StkStatus.PENDING, null, e.getMessage());
        }
    }

    // Daraja reports its real reason in the error body
    // ({"requestId":..,"errorCode":"400.002.02","errorMessage":".."}), not
    // in the HTTP status line, so that body is what gets carried forward.
    private MpesaException darajaError(String context, RestClientResponseException e) {
        String errorCode = null;
        String errorMessage = null;
        try {
            JsonNode node = JSON.readTree(e.getResponseBodyAsString());
            errorCode = node.path("errorCode").asText(null);
            errorMessage = node.path("errorMessage").asText(null);
        } catch (Exception ignored) {
            // Not JSON (an HTML error page from a proxy, say) - fall back
            // to the status line below rather than losing the failure.
        }
        String detail = errorMessage != null
            ? (errorCode == null ? errorMessage : errorCode + " - " + errorMessage)
            : e.getStatusCode() + " " + e.getResponseBodyAsString();
        return new MpesaException(errorCode, context + ": " + detail, e);
    }

    // ResultCode/ResponseCode come back as a JSON string on some Daraja
    // endpoints and a bare number on others, so neither a cast to String
    // nor one to Integer is safe on its own.
    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public record B2CResult(String conversationId, String originatorConversationId) {}

    /**
     * Initiates a real B2C payout ("Send Money"). Same contract as
     * initiateStkPush(): throws on failure, callers must catch and fall
     * back gracefully. CommandID "BusinessPayment" fits paying a partner/
     * vendor for an expense or a loan repayment - not a salary or a
     * promotional payout, which use different CommandIDs Safaricom expects
     * for reporting purposes.
     */
    public B2CResult initiateB2C(String phoneNumber, BigDecimal amount, String remarks, String occasion) {
        String accessToken = fetchAccessToken();
        String normalizedPhone = normalizePhone(phoneNumber);

        Map<String, Object> body = new LinkedHashMap<>();
        // v3 requires our own caller-generated reference (Safaricom's docs
        // example: "600997_Test_32et3241ed8yu") - not returned by
        // anything we call, so a fresh one is minted per request.
        body.put("OriginatorConversationID", b2cShortcode + "_" + UUID.randomUUID());
        body.put("InitiatorName", b2cInitiatorName);
        body.put("SecurityCredential", encryptSecurityCredential());
        body.put("CommandID", "BusinessPayment");
        body.put("Amount", amount.setScale(0, RoundingMode.DOWN).toPlainString());
        body.put("PartyA", b2cShortcode);
        body.put("PartyB", normalizedPhone);
        body.put("Remarks", remarks);
        body.put("QueueTimeOutURL", b2cQueueTimeoutUrl);
        body.put("ResultURL", b2cResultUrl);
        body.put("Occasion", occasion == null ? "" : occasion);

        Map<String, Object> response = restClient.post()
            .uri("/mpesa/b2c/v3/paymentrequest")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        return new B2CResult(
            (String) response.get("ConversationID"),
            (String) response.get("OriginatorConversationID")
        );
    }

    // Safaricom's documented scheme: RSA-encrypt the initiator password
    // with their public certificate (X.509, provided per-environment from
    // the Daraja portal), then base64 it. There's no real cert file yet -
    // isB2CConfigured() checks it exists before this is ever called, so
    // this only runs once one is actually in place.
    private String encryptSecurityCredential() {
        try (InputStream certStream = new FileInputStream(b2cCertPath)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(certStream);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, certificate.getPublicKey());
            byte[] encrypted = cipher.doFinal(b2cInitiatorPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't encrypt the B2C security credential", e);
        }
    }

    // Daraja tokens live ~1 hour. Reusing one saves a round trip per payment,
    // and the sandbox host is the flaky part: a connect timeout on this call
    // alone used to fail the whole payout, so it is retried once.
    private volatile String cachedToken;
    private volatile long cachedTokenExpiresAt;

    private String fetchAccessToken() {
        String cached = cachedToken;
        if (cached != null && System.currentTimeMillis() < cachedTokenExpiresAt) {
            return cached;
        }
        String credentials = Base64.getEncoder().encodeToString(
            (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8)
        );
        Map<String, Object> response = null;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 2 && response == null; attempt++) {  // each try already retries dead addresses
            try {
                response = restClient.get()
                    .uri("/oauth/v1/generate?grant_type=client_credentials")
                    .header("Authorization", "Basic " + credentials)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (RestClientResponseException e) {
                // A wrong/expired consumer key or secret lands here as a 400,
                // and is worth naming precisely - it's indistinguishable from
                // "the push was refused" once the reason has been thrown away.
                throw darajaError("Safaricom rejected the app credentials", e);
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (response == null) {
            throw new MpesaException(null, "Couldn't reach Safaricom to authenticate: "
                + (lastFailure == null ? "no response" : lastFailure.getMessage()), lastFailure);
        }
        String accessToken = asText(response.get("access_token"));
        if (accessToken == null) {
            throw new MpesaException(null, "Safaricom returned no access token", null);
        }
        cachedToken = accessToken;
        cachedTokenExpiresAt = System.currentTimeMillis() + 50L * 60 * 1000;
        return accessToken;
    }

    // Accepts 07xxxxxxxx / 01xxxxxxxx / 2547xxxxxxxx / +2547xxxxxxxx and
    // normalizes to Daraja's expected 2547xxxxxxxx / 2541xxxxxxxx form.
    //
    // "check the number carefully in the send money or stk push a number
    // can only be a 10 digit number tried an 11 and it was not stopped.
    // only +254 numbers are allowed" - this used to just strip non-digits
    // and prepend 254 with no length/prefix check at all, so a malformed
    // number (one digit too many, a landline, a non-Kenyan number) sailed
    // straight through to Safaricom instead of being caught here with a
    // clear message. Public (and a ResponseStatusException, not
    // MpesaException) so callers can validate up front, before their own
    // "Daraja unreachable/not configured" catch-all would otherwise
    // silently swallow a plain input mistake as if it were that.
    public String validateAndNormalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        String normalized;
        if (digits.startsWith("0")) {
            normalized = "254" + digits.substring(1);
        } else if (digits.startsWith("254")) {
            normalized = digits;
        } else {
            normalized = "254" + digits;
        }
        // 254 + 9 digits = 12 total; a real Safaricom/Airtel Kenya mobile
        // number always starts 2547 or 2541 after normalization.
        if (normalized.length() != 12 || !(normalized.startsWith("2547") || normalized.startsWith("2541"))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Enter a valid Kenyan phone number (07XXXXXXXX, 01XXXXXXXX, or 2547XXXXXXXX)"
            );
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        return validateAndNormalizePhone(phone);
    }
}
