package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.IngestSmsRequest;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.SmsQueueService;

@RestController
@RequestMapping("/api/sms-queue")
public class SmsQueueController {

    private final SmsQueueService smsQueueService;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public SmsQueueController(SmsQueueService smsQueueService, JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.smsQueueService = smsQueueService;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // "only one phone the company phone will have the ability to read
    // the offline SMS messages" - this used to take anything, from
    // anyone, no authentication at all. Now it's a real signed-in user,
    // and specifically the one account flagged Can_Sync_Sms - everyone
    // else's app hides the screen (AuthResponse.canSyncSms), and even a
    // direct call here is rejected regardless.
    @PostMapping
    public Map<String, Object> ingest(
        @RequestBody IngestSmsRequest request,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
        Boolean canSync = jdbcTemplate.queryForObject(
            "SELECT Can_Sync_Sms FROM USERS WHERE User_ID = ? AND Is_Deleted = FALSE", Boolean.class, userId
        );
        if (canSync == null || !canSync) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account isn't authorized to sync SMS messages");
        }
        return smsQueueService.ingest(request);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return smsQueueService.list(status);
    }

    // Runs the sweep on demand instead of waiting for the schedule -
    // useful for testing this feature without a two-minute wait each time.
    @PostMapping("/reconcile")
    public void reconcile() {
        smsQueueService.reconcileAll();
    }
}
