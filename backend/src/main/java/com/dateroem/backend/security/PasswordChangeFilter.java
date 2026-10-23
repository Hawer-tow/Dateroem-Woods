package com.dateroem.backend.security;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After an admin resets a password (migration 039) the account can do one
 * thing only: choose a password of its own. Every other call made with its
 * token is answered PASSWORD_CHANGE_REQUIRED, so the temporary password the
 * admin saw can't be used for anything else - whichever app it is typed
 * into. Runs after the phone and computer checks.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PasswordChangeFilter extends OncePerRequestFilter {

    public static final String CODE = "PASSWORD_CHANGE_REQUIRED";
    private static final Set<String> ALLOWED = Set.of(
        "/api/auth/me", "/api/auth/me/password", "/api/auth/login", "/api/auth/register", "/api/auth/google",
        "/api/auth/providers", "/api/auth/computer/challenge", "/api/auth/device/challenge", "/api/health"
    );

    private final JwtService jwtService;
    private final AccountSecurity accountSecurity;

    public PasswordChangeFilter(JwtService jwtService, AccountSecurity accountSecurity) {
        this.jwtService = jwtService;
        this.accountSecurity = accountSecurity;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader("Authorization") == null || ALLOWED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Optional<Integer> userId = jwtService.validate(request.getHeader("Authorization"));
        if (userId.isPresent() && accountSecurity.state(userId.get()).mustChangePassword()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":403,\"message\":\"" + CODE
                + ": Please choose a new password before carrying on.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
