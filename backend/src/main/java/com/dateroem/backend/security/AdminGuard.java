package com.dateroem.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.service.RoleService;

/**
 * Shared server-side admin gate for the desktop control app's endpoints
 * ("serious allowances to the system") - unlike most of this backend,
 * which returns role to the client and trusts it to gate its own UI, the
 * desktop app's admin-only surface (user management, raw table browsing)
 * is real enough that it needs to actually be enforced here, not just in
 * whichever client happens to call it.
 */
@Component
public class AdminGuard {

    private final JwtService jwtService;
    private final RoleService roleService;

    public AdminGuard(JwtService jwtService, RoleService roleService) {
        this.jwtService = jwtService;
        this.roleService = roleService;
    }

    public int requireAdmin(String authHeader) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        if (!roleService.isAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        return userId;
    }

    // Bulk Orders showcase pictures: "a marketer admin or superadmin can edit
    // it" - marketing material, so Marketers as well as admins.
    public int requireShowcaseEditor(String authHeader) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        String role = roleService.roleNameOf(userId);
        if (!(RoleName.Marketer.name().equals(role) || roleService.isAdmin(userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Marketer, Admin or SuperAdmin only");
        }
        return userId;
    }

    // "there can only be one super admin ... the one who permits phones
    // desktops is the superadmin" - approving and blocking phones and
    // computers is the one thing above Admin.
    public int requireSuperAdmin(String authHeader) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        if (!RoleName.SuperAdmin.name().equals(roleService.roleNameOf(userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SuperAdmin only");
        }
        return userId;
    }
}
