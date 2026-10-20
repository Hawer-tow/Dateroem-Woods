package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.AssignTransactionRequest;
import com.dateroem.backend.dto.ConfirmSendMoneyRequest;
import com.dateroem.backend.dto.ConfirmStkPushRequest;
import com.dateroem.backend.dto.SendMoneyRequest;
import com.dateroem.backend.dto.StkPushRequest;
import com.dateroem.backend.service.RoleService;
import com.dateroem.backend.service.TransactionService;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final RoleService roleService;

    public TransactionController(TransactionService transactionService, RoleService roleService) {
        this.transactionService = transactionService;
        this.roleService = roleService;
    }

    @PostMapping("/claim")
    public List<Integer> claim(@RequestHeader("X-Actor-User-Id") int actorUserId,
                                @RequestParam(defaultValue = "7") int batchSize) {
        return transactionService.claimBatch(actorUserId, batchSize);
    }

    @GetMapping("/claimed")
    public List<Map<String, Object>> claimed(@RequestHeader("X-Actor-User-Id") int actorUserId) {
        return transactionService.listClaimedBy(actorUserId);
    }

    @GetMapping("/unassigned")
    public List<Map<String, Object>> unassigned() {
        return transactionService.listUnassigned();
    }

    @PostMapping("/{financialTransactionId}/release")
    public void release(@PathVariable int financialTransactionId) {
        transactionService.release(financialTransactionId);
    }

    @GetMapping("/{financialTransactionId}/candidates")
    public List<Map<String, Object>> candidates(@PathVariable int financialTransactionId) {
        return transactionService.candidates(financialTransactionId);
    }

    @PostMapping("/{financialTransactionId}/assign")
    public void assign(@PathVariable int financialTransactionId,
                        @RequestBody AssignTransactionRequest body,
                        @RequestHeader("X-Actor-User-Id") int actorUserId) {
        transactionService.assign(financialTransactionId, body.target(), body.targetRowId(), actorUserId);
    }

    // "the transaction section also have the stk push section and this
    // should also work for any user since it is mostly for bulk goods" -
    // open to any signed-in actor, no pre-targeting (business owner
    // correction: STK only pulls money FROM a customer, never pre-targets
    // an expense category - see SendMoneyRequest for paying one out).
    @PostMapping("/stk-push")
    public Map<String, Object> stkPush(@RequestBody StkPushRequest request,
                                        @RequestHeader("X-Actor-User-Id") int actorUserId) {
        return transactionService.stkPush(request.amount(), request.phoneNumber(), actorUserId);
    }

    @PostMapping("/{financialTransactionId}/stk-push/confirm")
    public Map<String, Object> confirmStkPush(@PathVariable int financialTransactionId,
                                               @RequestBody ConfirmStkPushRequest request,
                                               @RequestHeader("X-Actor-User-Id") int actorUserId) {
        return transactionService.confirmStkPush(financialTransactionId, request.referenceId(), actorUserId);
    }

    // "Send Money" pays a specific expense holder out to a partner -
    // admin-only, since this actually moves company funds (same bar as
    // assign()/claim(), which this piggybacks on for its "already resolved,
    // don't re-offer it in the claim pool" behavior).
    @PostMapping("/send-money")
    public Map<String, Object> sendMoney(@RequestBody SendMoneyRequest request,
                                          @RequestHeader("X-Actor-User-Id") int actorUserId) {
        requireAdmin(actorUserId);
        return transactionService.sendMoney(request.amount(), request.phoneNumber(), request.target(), request.targetRowId(), actorUserId);
    }

    // Polled by the apps after a payout is sent: PENDING / COMPLETED (with the
    // M-Pesa receipt, filled by the result callback) / FAILED (with the reason).
    @org.springframework.web.bind.annotation.GetMapping("/{financialTransactionId}/status")
    public Map<String, Object> paymentStatus(@PathVariable int financialTransactionId) {
        return transactionService.paymentStatus(financialTransactionId);
    }

    // Who/how much an expense entry pays out to, worked out from the entry.
    @org.springframework.web.bind.annotation.GetMapping("/send-money/recipient")
    public Map<String, Object> sendMoneyRecipient(
        @org.springframework.web.bind.annotation.RequestParam com.dateroem.backend.model.ExpenseTarget target,
        @org.springframework.web.bind.annotation.RequestParam int rowId
    ) {
        return transactionService.sendMoneyRecipient(target, rowId);
    }

    @PostMapping("/{financialTransactionId}/send-money/confirm")
    public Map<String, Object> confirmSendMoney(@PathVariable int financialTransactionId,
                                                 @RequestBody ConfirmSendMoneyRequest request,
                                                 @RequestHeader("X-Actor-User-Id") int actorUserId) {
        return transactionService.confirmSendMoney(financialTransactionId, request.referenceId(), actorUserId);
    }

    private void requireAdmin(int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
    }
}
