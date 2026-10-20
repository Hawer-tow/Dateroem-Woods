package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.ShowcaseOrderRequest;
import com.dateroem.backend.dto.ShowcaseRequest;
import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.service.ShowcaseService;

// The Bulk Orders page's showcase pictures (see ShowcaseService). Reading
// them is open (the website shows them to everyone); every change needs a
// signed-in Marketer, Admin or SuperAdmin.
@RestController
@RequestMapping("/api/showcase")
public class ShowcaseController {

    private final ShowcaseService showcaseService;
    private final AdminGuard adminGuard;

    public ShowcaseController(ShowcaseService showcaseService, AdminGuard adminGuard) {
        this.showcaseService = showcaseService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return showcaseService.list();
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @RequestBody ShowcaseRequest request) {
        return showcaseService.create(request, adminGuard.requireShowcaseEditor(authHeader));
    }

    @PatchMapping("/{showcaseId}")
    public Map<String, Object> updateCaption(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                             @PathVariable int showcaseId, @RequestBody ShowcaseRequest request) {
        return showcaseService.updateCaption(showcaseId, request, adminGuard.requireShowcaseEditor(authHeader));
    }

    @PutMapping("/order")
    public List<Map<String, Object>> reorder(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                             @RequestBody ShowcaseOrderRequest request) {
        return showcaseService.reorder(request.showcaseIds(), adminGuard.requireShowcaseEditor(authHeader));
    }

    @DeleteMapping("/{showcaseId}")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authHeader,
                       @PathVariable int showcaseId) {
        showcaseService.delete(showcaseId, adminGuard.requireShowcaseEditor(authHeader));
    }
}
