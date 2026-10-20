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

import com.dateroem.backend.service.ComputerService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Every request that comes in through the desktop app's server (the admin
 * server marks each call with X-Client: desktop) must carry a sign-in token
 * issued to an approved computer, and the computer must still be approved,
 * the account still enabled and still Admin or SuperAdmin - "a standalone
 * application that has higher privilege that can work even if the backend
 * is not in the same machine".
 *
 * As with MobileDeviceFilter, X-Actor-User-Id is replaced by the user id
 * inside the verified token: before this, the desktop path took whatever
 * acting user the app sent, so anyone who could reach the admin server
 * could act as any admin - "Send Money" included - by changing a number.
 *
 * Website calls (no X-Client header) and mobile calls are not affected.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DesktopClientFilter extends OncePerRequestFilter {

    public static final String CLIENT = "desktop";
    // Set on every desktop request that passed, so endpoints only the
    // desktop app may use (approving computers) can tell.
    public static final String COMPUTER_ATTRIBUTE = "dateroem.computerId";
    private static final String ACTOR_HEADER = "X-Actor-User-Id";
    private static final Set<String> OPEN_PATHS = Set.of("/api/auth/login", "/api/auth/computer/challenge", "/api/health");

    private final JwtService jwtService;
    private final ComputerService computerService;

    public DesktopClientFilter(JwtService jwtService, ComputerService computerService) {
        this.jwtService = jwtService;
        this.computerService = computerService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CLIENT.equalsIgnoreCase(request.getHeader(MobileDeviceFilter.CLIENT_HEADER)) || OPEN_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Optional<JwtService.TokenClaims> claims = jwtService.parse(request.getHeader("Authorization"));
        if (claims.isEmpty() || claims.get().computerId() == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "SESSION_EXPIRED: Please sign in again.");
            return;
        }
        int userId = claims.get().userId();
        int computerId = claims.get().computerId();
        if (!computerService.isActive(userId, computerId)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED,
                ComputerService.REVOKED + ": This computer is no longer approved for your account, or the account was disabled "
                    + "or is no longer an admin. Ask the SuperAdmin.");
            return;
        }
        request.setAttribute(COMPUTER_ATTRIBUTE, computerId);
        chain.doFilter(new ActorRequest(request, String.valueOf(userId)), response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Same shape as Spring's own error body, so the admin server passes
        // the message on the way it already does for every other error.
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
