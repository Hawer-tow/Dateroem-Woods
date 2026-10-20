package com.dateroem.backend.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dateroem.backend.service.DeviceService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Every request that comes in through the mobile server (the only server
 * reachable from the internet for staff work - it marks each call with
 * X-Client: mobile) must carry a sign-in token issued to an approved phone,
 * and the phone must still be approved and the account still enabled.
 *
 * This also closes the old gap where the acting user was simply whatever
 * X-Actor-User-Id the app sent: for mobile calls that header is replaced by
 * the user id inside the verified token, so nobody can act as someone else
 * by changing a number.
 *
 * Website and desktop calls (no X-Client header) are not affected.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MobileDeviceFilter extends OncePerRequestFilter {

    public static final String CLIENT_HEADER = "X-Client";
    private static final String ACTOR_HEADER = "X-Actor-User-Id";
    private static final Set<String> OPEN_PATHS = Set.of("/api/auth/login", "/api/auth/device/challenge", "/api/health");

    private final JwtService jwtService;
    private final DeviceService deviceService;

    public MobileDeviceFilter(JwtService jwtService, DeviceService deviceService) {
        this.jwtService = jwtService;
        this.deviceService = deviceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"mobile".equalsIgnoreCase(request.getHeader(CLIENT_HEADER)) || OPEN_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Optional<JwtService.TokenClaims> claims = jwtService.parse(request.getHeader("Authorization"));
        if (claims.isEmpty() || claims.get().deviceId() == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "SESSION_EXPIRED: Please sign in again.");
            return;
        }
        int userId = claims.get().userId();
        if (!deviceService.isActive(userId, claims.get().deviceId())) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                DeviceService.REVOKED + ": This phone is no longer approved for your account, or the account was disabled. Ask the SuperAdmin.");
            return;
        }
        chain.doFilter(new ActorRequest(request, String.valueOf(userId)), response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Same shape as Spring's own error body, so the servers pass the
        // message on the way they already do for every other error.
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message.replace("\"", "'") + "\"}");
    }

    // The request as the controllers see it: X-Actor-User-Id is always the
    // signed-in user, whatever the app sent.
    private static final class ActorRequest extends HttpServletRequestWrapper {
        private final String actorId;

        ActorRequest(HttpServletRequest request, String actorId) {
            super(request);
            this.actorId = actorId;
        }

        @Override
        public String getHeader(String name) {
            return ACTOR_HEADER.equalsIgnoreCase(name) ? actorId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return ACTOR_HEADER.equalsIgnoreCase(name) ? Collections.enumeration(Set.of(actorId)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(ACTOR_HEADER);
            return Collections.enumeration(names);
        }
    }
}
