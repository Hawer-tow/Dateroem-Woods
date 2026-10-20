package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CreateUserRequest;
import com.dateroem.backend.dto.SetUserStatusRequest;
import com.dateroem.backend.dto.UpdateUserRequest;
import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.UserService;

// Admin-only, for the desktop control app - not exposed to the website or
// mobile app. Exception: lookup-by-phone (below), which only needs a real
// signed-in user, not an admin - the mobile payout screen uses it to show
// a recipient's name as their phone number is typed/edited.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AdminGuard adminGuard;
    private final JwtService jwtService;

    public UserController(UserService userService, AdminGuard adminGuard, JwtService jwtService) {
        this.userService = userService;
        this.adminGuard = adminGuard;
        this.jwtService = jwtService;
    }

    @GetMapping("/lookup-by-phone")
    public Map<String, Object> lookupByPhone(
        @RequestParam String phone,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        jwtService.validate(authHeader).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        return userService.findByPhone(phone);
    }

    @GetMapping("/picker")
    public List<Map<String, Object>> listForPicker(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        jwtService.validate(authHeader).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        return userService.listForPicker();
    }

    @GetMapping
    public List<Map<String, Object>> list(
        @RequestParam(required = false) Integer limit,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        adminGuard.requireAdmin(authHeader);
        return userService.list(limit);
    }

    @GetMapping("/{userId}")
    public Map<String, Object> get(
        @PathVariable int userId,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        adminGuard.requireAdmin(authHeader);
        return userService.get(userId);
    }

    @PostMapping
    public Map<String, Object> create(
        @RequestBody CreateUserRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        int actorUserId = adminGuard.requireAdmin(authHeader);
        int userId = userService.create(request, actorUserId);
        return userService.get(userId);
    }

    @PatchMapping("/{userId}")
    public Map<String, Object> update(
        @PathVariable int userId,
        @RequestBody UpdateUserRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        int actorUserId = adminGuard.requireAdmin(authHeader);
        userService.update(userId, request, actorUserId);
        return userService.get(userId);
    }

    @PatchMapping("/{userId}/status")
    public Map<String, Object> setStatus(
        @PathVariable int userId,
        @RequestBody SetUserStatusRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        int actorUserId = adminGuard.requireAdmin(authHeader);
        userService.setStatus(userId, request.active(), actorUserId);
        return userService.get(userId);
    }
}
