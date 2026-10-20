package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.service.DeviceService;

// One approved phone per account (see DeviceService). The challenge is open
// (the app needs it before it has signed in); admins see the list in the
// desktop app, and only the SuperAdmin approves or blocks ("the one who
// permits phones desktops is the superadmin").
@RestController
public class DeviceController {

    private final DeviceService deviceService;
    private final AdminGuard adminGuard;

    public DeviceController(DeviceService deviceService, AdminGuard adminGuard) {
        this.deviceService = deviceService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/api/auth/device/challenge")
    public Map<String, Object> challenge() {
        return deviceService.challenge();
    }

    // status: PENDING, ACTIVE, REVOKED, or omitted for every phone.
    @GetMapping("/api/devices")
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @RequestParam(required = false) String status) {
        adminGuard.requireAdmin(authHeader);
        return deviceService.list(status);
    }

    @PostMapping("/api/devices/{deviceId}/approve")
    public Map<String, Object> approve(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @PathVariable int deviceId,
                                       @RequestParam(defaultValue = "false") boolean replace) {
        int adminId = adminGuard.requireSuperAdmin(authHeader);
        return deviceService.approve(deviceId, replace, adminId);
    }

    @PostMapping("/api/devices/{deviceId}/revoke")
    public Map<String, Object> revoke(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @PathVariable int deviceId) {
        int adminId = adminGuard.requireSuperAdmin(authHeader);
        return deviceService.revoke(deviceId, adminId);
    }
}
