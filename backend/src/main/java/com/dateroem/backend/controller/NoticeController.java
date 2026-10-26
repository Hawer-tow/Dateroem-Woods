package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.NoticeRequest;
import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.service.NoticeService;

// The website's Updates (see NoticeService). Reading the live ones is open;
// everything else is for admins in the desktop app.
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;
    private final AdminGuard adminGuard;

    public NoticeController(NoticeService noticeService, AdminGuard adminGuard) {
        this.noticeService = noticeService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<Map<String, Object>> live() {
        return noticeService.live();
    }

    // deleted=true lists the bin (for Restore); otherwise every notice,
    // drafts and ended ones included.
    @GetMapping("/manage")
    public List<Map<String, Object>> manage(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestParam(defaultValue = "false") boolean deleted) {
        adminGuard.requireAdmin(authHeader);
        return noticeService.manage(deleted);
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @RequestBody NoticeRequest request) {
        return noticeService.create(request, adminGuard.requireAdmin(authHeader));
    }

    @PatchMapping("/{noticeId}")
    public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @PathVariable int noticeId, @RequestBody NoticeRequest request) {
        return noticeService.update(noticeId, request, adminGuard.requireAdmin(authHeader));
    }

    @DeleteMapping("/{noticeId}")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authHeader,
                       @PathVariable int noticeId) {
        noticeService.delete(noticeId, adminGuard.requireAdmin(authHeader));
    }

    @PostMapping("/{noticeId}/restore")
    public Map<String, Object> restore(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @PathVariable int noticeId) {
        return noticeService.restore(noticeId, adminGuard.requireAdmin(authHeader));
    }
}
