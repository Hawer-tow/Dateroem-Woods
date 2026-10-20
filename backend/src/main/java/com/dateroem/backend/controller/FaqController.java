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

import com.dateroem.backend.dto.FaqRequest;
import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.service.FaqService;

// The website's Help page questions (see FaqService). Reading the published
// ones is open; everything else is for admins in the desktop app.
@RestController
@RequestMapping("/api/faqs")
public class FaqController {

    private final FaqService faqService;
    private final AdminGuard adminGuard;

    public FaqController(FaqService faqService, AdminGuard adminGuard) {
        this.faqService = faqService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<Map<String, Object>> published() {
        return faqService.published();
    }

    // deleted=true lists the bin (for Restore); otherwise every question,
    // hidden ones included.
    @GetMapping("/manage")
    public List<Map<String, Object>> manage(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestParam(defaultValue = "false") boolean deleted) {
        adminGuard.requireAdmin(authHeader);
        return faqService.manage(deleted);
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @RequestBody FaqRequest request) {
        return faqService.create(request, adminGuard.requireAdmin(authHeader));
    }

    @PatchMapping("/{faqId}")
    public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @PathVariable int faqId, @RequestBody FaqRequest request) {
        return faqService.update(faqId, request, adminGuard.requireAdmin(authHeader));
    }

    @DeleteMapping("/{faqId}")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authHeader,
                       @PathVariable int faqId) {
        faqService.delete(faqId, adminGuard.requireAdmin(authHeader));
    }

    @PostMapping("/{faqId}/restore")
    public Map<String, Object> restore(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @PathVariable int faqId) {
        return faqService.restore(faqId, adminGuard.requireAdmin(authHeader));
    }
}
