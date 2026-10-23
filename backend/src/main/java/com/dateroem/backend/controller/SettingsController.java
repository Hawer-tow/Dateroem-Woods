package com.dateroem.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.AppSettings;

// Settings for the whole business (APP_SETTINGS, migration 039).
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AppSettings appSettings;
    private final AdminGuard adminGuard;
    private final JwtService jwtService;

    public SettingsController(AppSettings appSettings, AdminGuard adminGuard, JwtService jwtService) {
        this.appSettings = appSettings;
        this.adminGuard = adminGuard;
        this.jwtService = jwtService;
    }

    // "a timer like about 25 minutes of inactivity" - the minutes the phone and
    // desktop apps wait untouched before they lock; any signed-in person reads
    // it, only the SuperAdmin changes it ({"minutes": 5..120}).
    @GetMapping("/idle-lock")
    public Map<String, Object> idleLock(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        jwtService.validate(authHeader).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        return Map.of("minutes", appSettings.idleLockMinutes(),
            "min", AppSettings.MIN_IDLE_LOCK_MINUTES, "max", AppSettings.MAX_IDLE_LOCK_MINUTES);
    }

    // The company card on the phone's Home: any signed-in person reads it,
    // only the SuperAdmin changes it ({name, motto, code}).
    @GetMapping("/company")
    public Map<String, Object> company(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        jwtService.validate(authHeader).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        return appSettings.companyCard();
    }

    @PutMapping("/company")
    public Map<String, Object> setCompany(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @RequestBody Map<String, String> body) {
        int actorUserId = adminGuard.requireSuperAdmin(authHeader);
        Map<String, String> b = body == null ? Map.of() : body;
        return appSettings.setCompanyCard(b.get("name"), b.get("motto"), b.get("code"), actorUserId);
    }

    @PutMapping("/idle-lock")
    public Map<String, Object> setIdleLock(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                           @RequestBody Map<String, Integer> body) {
        int actorUserId = adminGuard.requireSuperAdmin(authHeader);
        return Map.of("minutes", appSettings.setIdleLockMinutes(body == null ? null : body.get("minutes"), actorUserId));
    }
}
