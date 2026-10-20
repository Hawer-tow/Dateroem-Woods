package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dateroem.backend.dto.IngestSmsRequest;
import com.dateroem.backend.util.JsonKeys;

/**
 * "one can send money away from the send money if the system is offline and
 * the message goes into the message app ... i want the system to check the
 * messages compare at times" - the company-SIM phone forwards every M-Pesa
 * SMS here (see ingest()); this reconciles each one against
 * FINANCIAL_TRANSACTION by its receipt code (Reference_ID), which is the
 * same column every online-confirmed payment already carries.
 *
 * Two outcomes per message, both already-existing flows on the receiving
 * end - nothing new for an admin to learn:
 *   - the receipt is already on a FINANCIAL_TRANSACTION row (the system was
 *     online when it happened) -> just linked for traceability, nothing to
 *     assign.
 *   - the receipt is nowhere in the system (paid while offline) -> a new,
 *     unassigned FINANCIAL_TRANSACTION row is created for it, and it drops
 *     straight into the same "unassigned transactions" pool/assign screen
 *     every STK/manual payment already uses (TransactionService.
 *     listUnassigned/assign) - Target_ID stays NULL until an admin picks
 *     what it pays for.
 *
 * Deliberately does NOT try to auto-complete one of our own PENDING Send
 * Money/STK templates by guessing on amount+timing - "this must be done in
 * care" - two pending payments of the same amount would make that a guess,
 * not a match. That case still surfaces as a new unassigned row instead,
 * same as any other unmatched receipt; a human confirms it either way.
 */
@Service
public class SmsQueueService {

    private static final Logger log = LoggerFactory.getLogger(SmsQueueService.class);

    private final JdbcTemplate jdbcTemplate;

    public SmsQueueService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Called by the phone as each SMS arrives. Idempotent on the receipt
     * code - re-sending the same message (app restart re-scanning the
     * inbox, a retried request) never queues it twice. */
    @Transactional
    public Map<String, Object> ingest(IngestSmsRequest request) {
        if (request.mpesaReferenceId() == null || request.mpesaReferenceId().isBlank()) {
            throw new IllegalArgumentException("mpesaReferenceId is required");
        }
        Integer existingId = jdbcTemplate.query(
            "SELECT Sms_Queue_ID FROM SMS_QUEUE WHERE Mpesa_Reference_ID = ? AND Is_Deleted = FALSE",
            rs -> rs.next() ? rs.getInt(1) : null,
            request.mpesaReferenceId()
        );
        if (existingId != null) {
            return Map.of("smsQueueId", existingId, "outcome", "already_queued");
        }

        jdbcTemplate.update(
            "INSERT INTO SMS_QUEUE (Sim_Slot, Mpesa_Reference_ID, Raw_Sms_Text, Amount, Transaction_Type, " +
            "Mpesa_Balance, Status, Created_At, Received_At) VALUES (?, ?, ?, ?, ?, ?, 'NEW', CURDATE(), COALESCE(?, CURDATE()))",
            request.simSlot(), request.mpesaReferenceId(), request.rawSmsText(), request.amount(),
            request.transactionType(), request.mpesaBalance(), request.receivedAt()
        );
        int smsQueueId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        String outcome = reconcileOne(smsQueueId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("smsQueueId", smsQueueId);
        response.put("outcome", outcome);
        return response;
    }

    /** The safety-net sweep - "check the messages compare at times" - for
     * anything an immediate reconcile at ingest time missed (a crash
     * mid-request, or a row some earlier version of this left as NEW). */
    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void reconcileAll() {
        List<Integer> pending = jdbcTemplate.queryForList(
            "SELECT Sms_Queue_ID FROM SMS_QUEUE WHERE Status = 'NEW' AND Is_Deleted = FALSE ORDER BY Sms_Queue_ID", Integer.class
        );
        if (pending.isEmpty()) {
            return;
        }
        log.info("SMS_QUEUE sweep: reconciling {} pending message(s)", pending.size());
        for (int id : pending) {
            try {
                reconcileOne(id);
            } catch (Exception e) {
                log.warn("Reconciling Sms_Queue_ID={} failed: {}", id, e.getMessage());
            }
        }
    }

    @Transactional
    public String reconcileOne(int smsQueueId) {
        Map<String, Object> sms;
        try {
            sms = jdbcTemplate.queryForMap(
                "SELECT Mpesa_Reference_ID, Amount, Status FROM SMS_QUEUE WHERE Sms_Queue_ID = ? AND Is_Deleted = FALSE",
                smsQueueId
            );
        } catch (EmptyResultDataAccessException e) {
            return "not_found";
        }
        if (!"NEW".equals(sms.get("Status"))) {
            return "already_reconciled";
        }
        String referenceId = (String) sms.get("Mpesa_Reference_ID");

        Integer existingTransactionId = jdbcTemplate.query(
            "SELECT Financial_Transaction_ID FROM FINANCIAL_TRANSACTION WHERE Reference_ID = ? AND Is_Deleted = FALSE",
            rs -> rs.next() ? rs.getInt(1) : null,
            referenceId
        );

        if (existingTransactionId != null) {
            // Already reconciled online (or an earlier SMS already covered
            // it) - just note where the confirmation came from.
            jdbcTemplate.update(
                "UPDATE FINANCIAL_TRANSACTION SET Sms_Queue_ID = COALESCE(Sms_Queue_ID, ?) WHERE Financial_Transaction_ID = ?",
                smsQueueId, existingTransactionId
            );
            jdbcTemplate.update("UPDATE SMS_QUEUE SET Status = 'MATCHED' WHERE Sms_Queue_ID = ?", smsQueueId);
            return "matched_existing";
        }

        // Genuinely new - paid through the raw M-Pesa app while the system
        // was offline. Lands unassigned, exactly like any other incoming
        // payment nobody has pointed at an expense/loan yet.
        BigDecimal amount = (BigDecimal) sms.get("Amount");
        jdbcTemplate.update(
            "INSERT INTO FINANCIAL_TRANSACTION (Payment_Mode, Reference_ID, Amount, Created_At, Sms_Queue_ID, Status) " +
            "VALUES ('SMS_QUEUE', ?, ?, CURDATE(), ?, 'COMPLETED')",
            referenceId, amount, smsQueueId
        );
        int financialTransactionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), NULL)",
            "FINANCIAL_TRANSACTION", financialTransactionId,
            "Created from company-SIM SMS (Sms_Queue_ID=" + smsQueueId + ", receipt=" + referenceId + ") - awaiting assignment"
        );
        jdbcTemplate.update("UPDATE SMS_QUEUE SET Status = 'PENDING_ASSIGNMENT' WHERE Sms_Queue_ID = ?", smsQueueId);
        return "created_unassigned";
    }

    public List<Map<String, Object>> list(String status) {
        if (status != null && !status.isBlank()) {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
                "SELECT * FROM SMS_QUEUE WHERE Is_Deleted = FALSE AND Status = ? ORDER BY Sms_Queue_ID DESC", status
            ));
        }
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT * FROM SMS_QUEUE WHERE Is_Deleted = FALSE ORDER BY Sms_Queue_ID DESC"
        ));
    }
}
