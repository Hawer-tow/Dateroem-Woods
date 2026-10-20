package com.dateroem.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.MpesaB2cResultRequest;
import com.dateroem.backend.dto.MpesaCallbackRequest;
import com.dateroem.backend.service.TransactionService;

/**
 * Public endpoint - Safaricom's own servers POST here directly, not a
 * signed-in app user, so it carries none of the usual X-Actor-User-Id/JWT
 * auth. app.mpesa.callback-url/b2c-result-url/b2c-queue-timeout-url must
 * point here once there's a reachable public HTTPS URL in front of it
 * (see MpesaClient's doc - localhost doesn't qualify, even for sandbox
 * testing; a tunnel like ngrok works for dev). The path is namespaced
 * with "dateroem" rather than a bare /callback, since a shared dev tunnel
 * host could just as easily be forwarding to some other project's server.
 *
 * The same handlers also answer at /api/dateroem-payments: Safaricom's
 * rules for callback addresses reject words such as "M-PESA" and
 * "Safaricom", so the production callback URLs use that path (see the
 * Hosting and Deployment Guide); the first path stays for the sandbox.
 */
@RestController
@RequestMapping({"/api/dateroem-mpesa-callback", "/api/dateroem-payments"})
public class MpesaCallbackController {

    private final TransactionService transactionService;

    public MpesaCallbackController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/stk")
    public Map<String, Object> callback(@RequestBody MpesaCallbackRequest request) {
        transactionService.handleMpesaCallback(request);
        // Daraja expects this exact ack shape regardless of what was done
        // with the payload - anything else gets retried repeatedly.
        return Map.of("ResultCode", 0, "ResultDesc", "Accepted");
    }

    // app.mpesa.b2c-result-url and app.mpesa.b2c-queue-timeout-url both
    // point here - Safaricom posts the same Result envelope shape to
    // either, so one handler covers both.
    @PostMapping("/b2c-result")
    public Map<String, Object> b2cResult(@RequestBody MpesaB2cResultRequest request) {
        transactionService.handleB2CResult(request);
        return Map.of("ResultCode", 0, "ResultDesc", "Accepted");
    }
}
