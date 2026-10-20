package com.dateroem.backend.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * "what are the recently added products ... Thanks for your question -
 * our team will get back to you shortly. why not provide a valid answer?"
 * - traced to the real AI call failing and the website silently falling
 * back to help.py's dumb keyword bot. The cause: this network occasionally
 * routes to a dead address for an external API (first seen with
 * Safaricom's sandbox - see MpesaClient's own history of this - now also
 * seen against Groq: "HTTP/1.1 header parser received no bytes"). DNS
 * caching is off JVM-wide (BackendApplication), so a retry usually lands
 * on a working address.
 *
 * Two variants because the retry-safety rules differ by what the call
 * actually does:
 *   - forReads(): retries any IOException, including a garbled/empty
 *     response like the Groq failure above - safe because a chat/scan
 *     call has no side effects to double up.
 *   - forPaymentSafeWrites(): only retries when nothing could possibly
 *     have been sent (a connect failure) - a POST that reached the far
 *     end (M-Pesa) is never blindly retried, since that could double-pay.
 */
public final class FlakyNetworkRetry {

    private FlakyNetworkRetry() {}

    private static final int ATTEMPTS = 5;

    public static ClientHttpRequestInterceptor forReads() {
        return (request, body, execution) -> attempt(request, body, execution, true);
    }

    public static ClientHttpRequestInterceptor forPaymentSafeWrites() {
        return (request, body, execution) -> attempt(request, body, execution, false);
    }

    private static ClientHttpResponse attempt(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution, boolean retryAnyIoException
    ) throws IOException {
        IOException last = null;
        for (int i = 1; i <= ATTEMPTS; i++) {
            try {
                return execution.execute(request, body);
            } catch (IOException e) {
                boolean retryable = retryAnyIoException || isConnectFailure(e) || request.getMethod() == HttpMethod.GET;
                if (!retryable) {
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
}
