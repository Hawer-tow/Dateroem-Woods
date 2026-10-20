package com.dateroem.backend.security;

import java.net.http.HttpClient;
import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.util.FlakyNetworkRetry;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.InvalidKeyException;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;

/**
 * "can my website have both the manual logging in and the google assisted
 * one" - checks the sign-in token the website's "Continue with Google"
 * button hands over. The token is signed by Google; it counts only if the
 * signature matches one of Google's published keys, it was issued by
 * Google, for this site's own client ID, and hasn't expired. Nothing the
 * browser says about the person is trusted without that.
 */
@Component
public class GoogleIdTokens {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    // Google rotates its keys every few days and publishes the next one
    // ahead of time - refetching a few times a day (and whenever a token
    // names a key we haven't seen) is plenty.
    private static final Duration KEYS_KEPT = Duration.ofHours(6);

    public record GoogleUser(String subject, String email, boolean emailVerified, String name) {}

    private final String clientId;
    private final String jwksUri;
    private final RestClient restClient;
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant keysFetchedAt = Instant.EPOCH;

    public GoogleIdTokens(
        @Value("${app.google.client-id:}") String clientId,
        @Value("${app.google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwksUri
    ) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.jwksUri = jwksUri;
        // HTTP/1.1 and retried reads, as for the other outside calls - this
        // network sometimes lands on a dead route (see FlakyNetworkRetry).
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(6))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .requestInterceptor(FlakyNetworkRetry.forReads())
            .build();
    }

    /** The client ID the website's button needs, or null when Google sign-in isn't set up. */
    public String clientId() {
        return clientId.isEmpty() ? null : clientId;
    }

    public GoogleUser verify(String idToken) {
        if (clientId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in isn't set up yet");
        }
        if (idToken == null || idToken.isBlank()) {
            throw notConfirmed();
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(JwsHeader header) {
                        return keyFor(header.getKeyId());
                    }
                })
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(idToken.trim())
                .getPayload();
        } catch (RestClientException e) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in can't be reached right now - please try again in a moment"
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw notConfirmed();
        }
        Set<String> audience = claims.getAudience();
        if (!ISSUERS.contains(claims.getIssuer()) || audience == null || !audience.contains(clientId)) {
            throw notConfirmed();
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw notConfirmed();
        }
        Object verified = claims.get("email_verified");
        return new GoogleUser(
            subject,
            claims.get("email", String.class),
            Boolean.TRUE.equals(verified) || "true".equals(verified),
            claims.get("name", String.class)
        );
    }

    private PublicKey keyFor(String keyId) {
        boolean fresh = Instant.now().isBefore(keysFetchedAt.plus(KEYS_KEPT));
        PublicKey key = keyId == null ? null : keys.get(keyId);
        if (key == null || !fresh) {
            refreshKeys();
            key = keyId == null ? null : keys.get(keyId);
        }
        if (key == null) {
            throw new InvalidKeyException("Not one of Google's signing keys");
        }
        return key;
    }

    private synchronized void refreshKeys() {
        // Another request may have just done it.
        if (Instant.now().isBefore(keysFetchedAt.plusSeconds(5))) {
            return;
        }
        String json = restClient.get().uri(jwksUri).retrieve().body(String.class);
        Map<String, PublicKey> fetched = new HashMap<>();
        for (Jwk<?> jwk : Jwks.setParser().build().parse(json).getKeys()) {
            if (jwk.getId() != null && jwk.toKey() instanceof PublicKey publicKey) {
                fetched.put(jwk.getId(), publicKey);
            }
        }
        keys = Map.copyOf(fetched);
        keysFetchedAt = Instant.now();
    }

    private static ResponseStatusException notConfirmed() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in couldn't be confirmed - please try again");
    }
}
