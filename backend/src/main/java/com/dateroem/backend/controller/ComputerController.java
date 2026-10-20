package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.security.DesktopClientFilter;
import com.dateroem.backend.service.ComputerService;

import jakarta.servlet.http.HttpServletRequest;

// The desktop app's approved computers (see ComputerService). The challenge
// is open (the app needs it before it has signed in); everything else only
// from the desktop app on an approved computer - admins see the list, and
// only the SuperAdmin approves or blocks ("the one who permits phones
// desktops is the superadmin").
@RestController
public class ComputerController {

    private final ComputerService computerService;
    private final AdminGuard adminGuard;

    public ComputerController(ComputerService computerService, AdminGuard adminGuard) {
        this.computerService = computerService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/api/auth/computer/challenge")
    public Map<String, Object> challenge() {
        return computerService.challenge();
    }

    // status: PENDING, ACTIVE, REVOKED, or omitted for every computer.
    @GetMapping("/api/computers")
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @RequestParam(required = false) String status,
                                          HttpServletRequest request) {
        requireDesktop(request);
        adminGuard.requireAdmin(authHeader);
        return computerService.list(status);
    }

    @PostMapping("/api/computers/{computerId}/approve")
    public Map<String, Object> approve(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @PathVariable int computerId,
                                       @RequestParam(defaultValue = "false") boolean replace,
                                       HttpServletRequest request) {
        requireDesktop(request);
        int adminId = adminGuard.requireSuperAdmin(authHeader);
        return computerService.approve(computerId, replace, adminId);
    }

    @PostMapping("/api/computers/{computerId}/revoke")
    public Map<String, Object> revoke(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @PathVariable int computerId,
                                      HttpServletRequest request) {
        requireDesktop(request);
        int adminId = adminGuard.requireSuperAdmin(authHeader);
        return computerService.revoke(computerId, adminId);
    }

    // Set by DesktopClientFilter only once it has checked the computer.
    private static void requireDesktop(HttpServletRequest request) {
        if (request.getAttribute(DesktopClientFilter.COMPUTER_ATTRIBUTE) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Computers can only be managed from the desktop app");
        }
    }
}
