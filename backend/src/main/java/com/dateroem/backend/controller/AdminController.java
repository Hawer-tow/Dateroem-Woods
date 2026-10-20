package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.service.AdminDataService;

// Admin-only, desktop control app's raw database browser.
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDataService adminDataService;
    private final AdminGuard adminGuard;

    public AdminController(AdminDataService adminDataService, AdminGuard adminGuard) {
        this.adminDataService = adminDataService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/tables")
    public List<String> tables(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        adminGuard.requireAdmin(authHeader);
        return adminDataService.listTables();
    }

    // limit omitted (or explicitly blank) means "all" - "specifying all,
    // only 50 etc".
    @GetMapping("/tables/{tableName}")
    public Map<String, Object> browse(
        @PathVariable String tableName,
        @RequestParam(required = false) Integer limit,
        @RequestParam(defaultValue = "0") int offset,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        adminGuard.requireAdmin(authHeader);
        return adminDataService.browse(tableName, limit, offset);
    }
}
