package com.dateroem.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expiryHours;
    private final long desktopExpiryHours;
    // Tokens issued before this moment are refused. On 18 October 2026 the
    // clean-up before going live renumbered the accounts (users 1-4), so an
    // older token could name whichever account has that number now -
    // everyone signs in again once instead. 08:30 UTC is 11:30 in Kenya,
    // when the servers were stopped for the clean-up.
    // AUTH_TOKENS_VALID_FROM moves it.
    private final Instant validFrom;
    // Per account too: a password changed or reset signs out every sign-in
    // made before it (migration 039).
    private final AccountSecurity accountSecurity;

    public JwtService(
        @Value("${app.auth.jwt-secret}") String secret,
        @Value("${app.auth.jwt-expiry-hours}") long expiryHours,
        @Value("${app.auth.desktop-jwt-expiry-hours:12}") long desktopExpiryHours,
        @Value("${app.auth.tokens-valid-from:${AUTH_TOKENS_VALID_FROM:2026-10-18T08:30:00Z}}") String validFrom,
        AccountSecurity accountSecurity
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryHours = expiryHours;
        this.desktopExpiryHours = desktopExpiryHours;
        this.validFrom = validFrom == null || validFrom.isBlank() ? null : Instant.parse(validFrom.trim());
        this.accountSecurity = accountSecurity;
    }

    public String issueToken(int userId) {
        return issueToken(userId, null);
    }

    // A mobile-app sign-in carries the approved phone it was made on ("did"),
    // so the token is only good for as long as that phone stays approved
    // (see MobileDeviceFilter). Website tokens have none; desktop ones carry
    // a computer instead (issueComputerToken).
    public String issueToken(int userId, Integer deviceId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiryHours, ChronoUnit.HOURS)));
        if (deviceId != null) {
            builder.claim("did", deviceId);
        }
        return builder.signWith(key).compact();
    }

    // A desktop-app sign-in carries the approved computer it was made on
    // ("pc", see DesktopClientFilter) and lasts a working day rather than a
    // month: the desktop app is the most powerful client, and it signs in
    // again every time it is opened anyway.
    public String issueComputerToken(int userId, int computerId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(desktopExpiryHours, ChronoUnit.HOURS)))
            .claim("pc", computerId)
            .signWith(key)
            .compact();
    }

    // Returns the authenticated user id, or empty if the token is missing,
    // malformed, expired, or otherwise not trustworthy.
    public java.util.Optional<Integer> validate(String bearerHeader) {
        return parse(bearerHeader).map(TokenClaims::userId);
    }

    public record TokenClaims(int userId, Integer deviceId, Integer computerId) {}

    // Same checks as validate(), plus the phone or computer the token was
    // issued for (both null for a website sign-in).
    public java.util.Optional<TokenClaims> parse(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            return java.util.Optional.empty();
        }
        String token = bearerHeader.substring(7);
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Date issuedAt = claims.getIssuedAt();
            if (validFrom != null && (issuedAt == null || issuedAt.toInstant().isBefore(validFrom))) {
                return java.util.Optional.empty();
            }
            int userId = Integer.parseInt(claims.getSubject());
            // A token carries whole seconds, so one issued in the same second
            // as the change (the new one handed back) still counts.
            Instant changedAt = accountSecurity.state(userId).passwordChangedAt();
            if (changedAt != null && (issuedAt == null
                || issuedAt.toInstant().isBefore(changedAt.truncatedTo(ChronoUnit.SECONDS)))) {
                return java.util.Optional.empty();
            }
            Number deviceId = claims.get("did", Number.class);
            Number computerId = claims.get("pc", Number.class);
            return java.util.Optional.of(new TokenClaims(
                userId, deviceId == null ? null : deviceId.intValue(),
                computerId == null ? null : computerId.intValue()
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
