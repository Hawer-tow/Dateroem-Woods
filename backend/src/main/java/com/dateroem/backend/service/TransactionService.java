package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.MpesaB2cResultRequest;
import com.dateroem.backend.dto.MpesaCallbackRequest;
import com.dateroem.backend.model.ExpenseTarget;
import com.dateroem.backend.util.JsonKeys;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final MpesaClient mpesaClient;
    private final OrderService orderService;
    private final RoleService roleService;

    public TransactionService(JdbcTemplate jdbcTemplate, MpesaClient mpesaClient, OrderService orderService, RoleService roleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mpesaClient = mpesaClient;
        this.orderService = orderService;
        this.roleService = roleService;
    }

    /**
     * Atomically claims up to batchSize unassigned FINANCIAL_TRANSACTION
     * rows for the given user, so concurrent admins split the unassigned
     * pool instead of colliding on the same rows (business rules: "if the
     * FINANCIAL_TRANSACTION table has 14 transactions not assigned and
     * another user logs in it can reduce one's load to 7 for each").
     * SELECT ... FOR UPDATE SKIP LOCKED guarantees no two callers can claim
     * the same row (requires migration 002's Claimed_By/Claimed_At columns).
     */
    @Transactional
    public List<Integer> claimBatch(int userId, int batchSize) {
        List<Integer> ids = jdbcTemplate.queryForList(
            "SELECT Financial_Transaction_ID FROM FINANCIAL_TRANSACTION " +
            "WHERE Claimed_By IS NULL AND Target_ID IS NULL AND Is_Deleted = FALSE " +
            "ORDER BY Created_At LIMIT ? FOR UPDATE SKIP LOCKED",
            Integer.class, batchSize
        );

        if (!ids.isEmpty()) {
            String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
            Object[] params = new Object[ids.size() + 1];
            params[0] = userId;
            for (int i = 0; i < ids.size(); i++) {
                params[i + 1] = ids.get(i);
            }
            jdbcTemplate.update(
                "UPDATE FINANCIAL_TRANSACTION SET Claimed_By = ?, Claimed_At = NOW() " +
                "WHERE Financial_Transaction_ID IN (" + placeholders + ")",
                params
            );
        }

        return ids;
    }

    public void release(int financialTransactionId) {
        jdbcTemplate.update(
            "UPDATE FINANCIAL_TRANSACTION SET Claimed_By = NULL, Claimed_At = NULL " +
            "WHERE Financial_Transaction_ID = ?",
            financialTransactionId
        );
    }

    public List<Map<String, Object>> listClaimedBy(int userId) {
        // "order the transactions from the latest to the oldest for quick
        // identification" - same ordering as listUnassigned() below.
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT * FROM FINANCIAL_TRANSACTION WHERE Claimed_By = ? AND Is_Deleted = FALSE " +
            "ORDER BY Created_At DESC, Financial_Transaction_ID DESC",
            userId
        ));
    }

    // "you ask any unassigned transactions? it will provide them and when
    // you click them it will lead you to being taken to the assign
    // transaction" - a plain read-only view for admin insight, distinct
    // from claimBatch() above (which mutates Claimed_By - built for the
    // staff work-queue flow, not for just looking). Includes anything
    // still unassigned regardless of claim state, so an admin can see the
    // full picture including what's already claimed by someone.
    public List<Map<String, Object>> listUnassigned() {
        // "order the transactions from the latest to the oldest for quick
        // identification" - Created_At only stores a date (no time), so a
        // same-day tiebreaker on the ID (insertion order) keeps today's
        // batch stable and genuinely newest-first instead of arbitrary.
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT * FROM FINANCIAL_TRANSACTION WHERE Target_ID IS NULL AND Is_Deleted = FALSE " +
            "ORDER BY Created_At DESC, Financial_Transaction_ID DESC"
        ));
    }

    /**
     * The admin assign screen's suggestion list - business rules: "the
     * system checks for a record of 20 ksh in the holders and provides a
     * list if they are many and also checks when they were recorded and
     * the one that almost matches is assigned that". Searches every
     * auto-matchable ExpenseTarget category (LOAN is excluded - Loans &
     * Finance is filled directly by an admin, not matched here) for still-
     * unassigned rows with the exact same amount, ranked by how close their
     * own creation time is to this transaction's - the admin picks from
     * this ranked list rather than it auto-assigning (money handling should
     * never silently guess). This is for incoming payments only (SMS/STK)
     * with no known target yet - Send Money always knows its target
     * upfront and skips this entirely.
     */
    public List<Map<String, Object>> candidates(int financialTransactionId) {
        Map<String, Object> transaction;
        try {
            transaction = jdbcTemplate.queryForMap(
                "SELECT Amount, Created_At FROM FINANCIAL_TRANSACTION WHERE Financial_Transaction_ID = ? AND Is_Deleted = FALSE",
                financialTransactionId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        BigDecimal amount = (BigDecimal) transaction.get("Amount");
        Object createdAt = transaction.get("Created_At");

        List<Map<String, Object>> combined = new ArrayList<>();
        for (ExpenseTarget target : ExpenseTarget.values()) {
            if (!target.autoMatchable()) {
                continue;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + target.idColumn() + " AS rowId, " + target.amountColumn() + " AS amount, " +
                target.createdAtColumn() + " AS createdAt, " +
                "ABS(TIMESTAMPDIFF(SECOND, " + target.createdAtColumn() + ", ?)) AS timeDiffSeconds " +
                "FROM " + target.tableName() + " " +
                "WHERE " + target.amountColumn() + " = ? AND Financial_Transaction_ID IS NULL AND Is_Deleted = FALSE " +
                "ORDER BY timeDiffSeconds IS NULL, timeDiffSeconds ASC LIMIT 5",
                createdAt, amount
            );
            for (Map<String, Object> row : rows) {
                row.put("target", target.name());
                row.put("category", target.categoryKey());
                combined.add(row);
            }
        }

        combined.sort(Comparator.comparing(
            row -> row.get("timeDiffSeconds") == null ? Long.MAX_VALUE : ((Number) row.get("timeDiffSeconds")).longValue()
        ));
        return combined.size() > 10 ? combined.subList(0, 10) : combined;
    }

    /**
     * Records an STK push prompt as a pending FINANCIAL_TRANSACTION, then -
     * if Daraja credentials are configured (MpesaClient.isConfigured()) -
     * actually sends the live prompt and stores the CheckoutRequestID to
     * correlate Safaricom's callback back to this row. Sandbox testing
     * today has no real credentials, so this quietly no-ops (darajaInitiated
     * = false) and the manual confirmStkPush() flow stays the way to finish
     * testing this pathway until "later we ... fill the correct
     * credentials". A Daraja call that fails (bad creds, network) is caught
     * and logged rather than failing the whole request - same reasoning.
     *
     * STK pulls money FROM a customer - business owner correction: it's
     * only for a customer paying for a product (website) or a bulk-goods
     * payment (any user can send one, no admin gate, no pre-targeting to
     * an expense category - that's SendMoneyRequest's job instead, since
     * expenses are money the business pays OUT to a partner).
     *
     * Deliberately NOT @Transactional: Safaricom's sandbox can fire the
     * STK callback within milliseconds of accepting the push, on a
     * separate thread - if the Checkout_Request_ID UPDATE below were
     * still waiting inside an open transaction for this whole method to
     * return, handleMpesaCallback()'s lookup could run before that commit
     * and log a real, successful callback as "unknown" (caught via live
     * sandbox testing). Each statement here commits on its own instead,
     * so the row is visible to the callback as soon as each step finishes
     * rather than only once the whole request-response cycle completes.
     */
    // Every amount that will move through M-Pesa - a prompt pulled from a
    // customer, a payout sent to a partner, a loan repayment - is checked
    // here before any row is written, so a negative, zero, fractional or
    // absurd figure can't reach the books or Safaricom. M-Pesa deals in
    // whole shillings, and MAX_PAYMENT is well above any genuine single
    // Dateroem payment while still blocking a runaway value (a typo with an
    // extra digit, or a tampered request).
    private static final BigDecimal MAX_PAYMENT = new BigDecimal("1000000");

    private void validatePaymentAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The amount must be more than zero");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The amount must be a whole number of shillings");
        }
        if (amount.compareTo(MAX_PAYMENT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That amount is too large - check the figure");
        }
    }

    public Map<String, Object> stkPush(BigDecimal amount, String phoneNumber, int actorUserId) {
        // "check the number carefully in the send money or stk push ...
        // tried an 11 [digit number] and it was not stopped" - validated
        // up front, before the transaction row even gets created, so a
        // plain input mistake gets a clear 400 instead of being recorded
        // as a silent "Daraja not configured/unreachable" no-op by the
        // catch-all below.
        validatePaymentAmount(amount);
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            mpesaClient.validateAndNormalizePhone(phoneNumber);
        }
        jdbcTemplate.update(
            "INSERT INTO FINANCIAL_TRANSACTION (Payment_Mode, Amount, Created_At) VALUES ('STK_PUSH', ?, CURDATE())",
            amount
        );
        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        boolean darajaInitiated = false;
        String checkoutRequestId = null;
        if (mpesaClient.isConfigured() && phoneNumber != null && !phoneNumber.isBlank()) {
            try {
                MpesaClient.StkPushResult result = mpesaClient.initiateStkPush(
                    phoneNumber, amount, "FT" + id, "Dateroem payment"
                );
                checkoutRequestId = result.checkoutRequestId();
                jdbcTemplate.update(
                    "UPDATE FINANCIAL_TRANSACTION SET Checkout_Request_ID = ? WHERE Financial_Transaction_ID = ?",
                    checkoutRequestId, id
                );
                darajaInitiated = true;
            } catch (Exception e) {
                log.warn("Daraja STK push failed for Financial_Transaction_ID={}: {}", id, e.getMessage());
            }
        }

        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "FINANCIAL_TRANSACTION", id,
            "STK push " + (darajaInitiated ? "sent via Daraja" : "recorded (Daraja not configured/unreachable)") +
                ", amount=" + amount + ", phone=" + phoneNumber,
            actorUserId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("financialTransactionId", id);
        response.put("darajaInitiated", darajaInitiated);
        response.put("checkoutRequestId", checkoutRequestId);
        return response;
    }

    /**
     * Stand-in for the Safaricom Daraja STK callback for as long as sandbox
     * testing has no real reachable callback URL - whoever sent the prompt
     * confirms it went through (they saw the M-Pesa SMS) with the receipt
     * code. Once app.mpesa.callback-url is a real public HTTPS endpoint,
     * handleMpesaCallback() below does this automatically instead; this
     * stays available either way as a manual fallback.
     */
    @Transactional
    public Map<String, Object> confirmStkPush(int financialTransactionId, String referenceId, int actorUserId) {
        return finalizeConfirmation(financialTransactionId, referenceId, actorUserId);
    }

    /**
     * The real Safaricom Daraja STK callback handler - correlates by
     * CheckoutRequestID (stkPush() stores it when Daraja is configured)
     * rather than by our own Financial_Transaction_ID, since Safaricom only
     * knows the former. ResultCode != 0 means the prompt was cancelled or
     * failed on the customer's end - logged, left pending for manual
     * follow-up (no "failed" status column exists yet to mark it more
     * visibly). No human actor initiated this call, so audit entries use a
     * null Actor_ID (the column allows it) rather than inventing one.
     *
     * READ_COMMITTED (not the connection's default REPEATABLE READ):
     * MySQL's REPEATABLE READ takes its consistent-read snapshot at a
     * transaction's first SELECT, so once findFinancialTransactionIdBy...'s
     * retry loop has read "not found" once, every later attempt in the
     * same transaction would keep re-reading that same stale snapshot,
     * even after the row is committed elsewhere - defeating the retry
     * entirely. READ_COMMITTED gives each attempt a fresh read.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleMpesaCallback(MpesaCallbackRequest request) {
        MpesaCallbackRequest.StkCallback callback = request.Body().stkCallback();
        String checkoutRequestId = callback.CheckoutRequestID();

        // This endpoint is public - Safaricom's servers reach it directly,
        // so anyone on the internet can POST a message to it that LOOKS like
        // a Safaricom result. The body is therefore never trusted on its
        // own: whatever it claims, we ask Safaricom with the authenticated
        // status query (queryStkStatus, signed with our own credentials) and
        // act only on that answer. A forged "success" can't make the query
        // say success, and a forged "failure" can't make it say failure. If
        // Safaricom can't confirm yet (still processing, or unreachable),
        // nothing is changed here - the order-confirmation poll, which uses
        // the same query, settles it moments later.
        MpesaClient.StkStatusResult confirmed = mpesaClient.queryStkStatus(checkoutRequestId);
        if (confirmed.status() == MpesaClient.StkStatus.PENDING) {
            log.info("STK callback for CheckoutRequestID={} not yet confirmable by Daraja - leaving it for the poll", checkoutRequestId);
            return;
        }
        boolean paid = confirmed.status() == MpesaClient.StkStatus.SUCCESS;

        Integer financialTransactionId = findFinancialTransactionIdByCheckoutRequestId(checkoutRequestId);
        if (financialTransactionId == null) {
            // Not a staff-initiated push (TransactionController's own
            // /stk-push) - the other thing that stores a CheckoutRequestID
            // is a website order's checkout, so try that before giving up.
            if (!paid) {
                if (!orderService.failPaymentByCheckoutRequestId(checkoutRequestId, confirmed.description())) {
                    log.warn("Daraja STK callback for unknown CheckoutRequestID={}", checkoutRequestId);
                }
                return;
            }
            // The query confirmed the payment; the receipt is only a
            // reference for the record, so the callback's one is used when
            // present and a plain note when not.
            String orderReceipt = callback.itemValue("MpesaReceiptNumber");
            if (!orderService.confirmPaymentByCheckoutRequestId(checkoutRequestId,
                    orderReceipt != null ? orderReceipt : "confirmed by Safaricom")) {
                log.warn("Daraja STK callback for unknown CheckoutRequestID={}", checkoutRequestId);
            }
            return;
        }

        if (!paid) {
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), NULL)",
                "FINANCIAL_TRANSACTION", financialTransactionId,
                "STK push not completed (" + confirmed.resultCode() + "): " + confirmed.description()
            );
            return;
        }

        String mpesaReceiptNumber = callback.itemValue("MpesaReceiptNumber");
        finalizeConfirmationQuietly(financialTransactionId,
            mpesaReceiptNumber != null ? mpesaReceiptNumber : "confirmed by Safaricom");
    }

    /**
     * "Send Money" (Daraja B2C) - business owner correction: "expenses are
     * payments done to the other partners ... i dont think its an stk push
     * but rather a send money option". Pays a specific already-recorded,
     * still-unassigned expense holder: unlike stkPush(), target/targetRowId
     * are required, since there's a known recipient/purpose upfront - no
     * candidate-matching needed once it's confirmed, it always auto-assigns.
     * LOAN itself is rejected here - repaying a loan goes through payLoan()
     * instead, which creates a fresh LOAN_REPAYMENTS row rather than paying
     * an existing one (a loan doesn't have a pre-existing "holder" the way
     * TRANSPORT/COMMUNICATIONS/etc. do).
     *
     * Not @Transactional, same reasoning as stkPush() - the B2C result
     * callback can race ahead of a still-open transaction's commit.
     */
    // A payout must go to the number the business has on file for the
    // recipient (a farmer, an employee's salary, the customer owed a
    // refund, the borrower of a loan). An admin sending to a DIFFERENT
    // number - the way money could be quietly diverted - is allowed only
    // for the SuperAdmin, and recorded. Categories with no one on file
    // (a general expense, a communications bill) still take a typed number
    // from any admin, since there is nothing to check it against.
    private void assertRecipientAllowed(ExpenseTarget target, int rowId, String phoneNumber, int actorUserId) {
        Object onFile = sendMoneyRecipient(target, rowId).get("recipientPhone");
        String onFileDigits = significantDigits(onFile == null ? null : onFile.toString());
        if (onFileDigits == null) {
            return;
        }
        if (!onFileDigits.equals(significantDigits(phoneNumber)) && !roleService.isSuperAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This payment can only go to the number on file for this recipient. Ask the SuperAdmin to send it to a different number.");
        }
        if (!onFileDigits.equals(significantDigits(phoneNumber))) {
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
                target.tableName(), rowId, "SuperAdmin sent " + target.categoryKey() + " #" + rowId + " to a number other than the one on file", actorUserId
            );
        }
    }

    // The part of a Kenyan phone number that identifies the line, ignoring
    // how it was written (0712..., 254712..., +254712...): the last nine
    // digits. null when there are not nine, so a blank or junk value never
    // counts as a match.
    private static String significantDigits(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= 9 ? digits.substring(digits.length() - 9) : null;
    }

    public Map<String, Object> sendMoney(BigDecimal amount, String phoneNumber, ExpenseTarget target, int targetRowId, int actorUserId) {
        validatePaymentAmount(amount);
        if (target == ExpenseTarget.LOAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the loan repayment endpoint to pay off a loan");
        }
        assertRecipientAllowed(target, targetRowId, phoneNumber, actorUserId);
        assertUnassignedHolderExists(target, targetRowId);
        return initiateOutgoingPayment(amount, phoneNumber, target, targetRowId, actorUserId,
            "Send Money", "Payment for " + target.categoryKey() + " #" + targetRowId);
    }

    /**
     * "Pay Loan" - business rules: "for each loan there will be an option
     * to click it and an option pay loan in it if the loan repayments have
     * not yet reached the expected amount to clear loan ... one will be
     * led to the transactions area and the option loan repayment for the
     * selected loan". Creates a brand-new LOAN_REPAYMENTS holder for this
     * payment (no pre-existing row to pay into, unlike sendMoney()'s
     * targets), then sends it the same way.
     *
     * Not @Transactional, same reasoning as stkPush().
     */
    public Map<String, Object> payLoan(int loanId, BigDecimal amount, String phoneNumber, int actorUserId) {
        validatePaymentAmount(amount);
        // The loan's borrower is on file (ExpenseTarget.LOAN reads the LOAN
        // row directly) - checked before the repayment row is created.
        assertRecipientAllowed(ExpenseTarget.LOAN, loanId, phoneNumber, actorUserId);
        jdbcTemplate.update(
            "INSERT INTO LOAN_REPAYMENTS (Amount_Paid, Payment_Date, Loan_ID) VALUES (?, CURDATE(), ?)",
            amount, loanId
        );
        int repaymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "LOAN_REPAYMENTS", repaymentId, "Repayment initiated for Loan_ID=" + loanId + ", amount=" + amount, actorUserId
        );

        Map<String, Object> result = initiateOutgoingPayment(amount, phoneNumber, ExpenseTarget.LOAN_REPAYMENTS, repaymentId,
            actorUserId, "Loan repayment", "Loan repayment for Loan_ID=" + loanId);
        result.put("loanRepaymentId", repaymentId);
        return result;
    }

    // Safaricom can fire the STK/B2C callback within milliseconds of
    // accepting the push - sometimes faster than our own synchronous
    // response finishes returning to the original caller and committing
    // the Checkout_Request_ID (observed live against the sandbox: a real,
    // successful callback logged as "unknown" and silently dropped). A
    // brief bounded retry absorbs that race instead of losing the payment
    // match; Safaricom's own timeout tolerance on this webhook is many
    // seconds, so the ~1.2s worst case here is safe.
    private Integer findFinancialTransactionIdByCheckoutRequestId(String checkoutRequestId) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return jdbcTemplate.queryForObject(
                    "SELECT Financial_Transaction_ID FROM FINANCIAL_TRANSACTION " +
                    "WHERE Checkout_Request_ID = ? AND Is_Deleted = FALSE",
                    Integer.class, checkoutRequestId
                );
            } catch (EmptyResultDataAccessException e) {
                if (attempt == 5) {
                    return null;
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private void assertUnassignedHolderExists(ExpenseTarget target, int targetRowId) {
        Integer count;
        try {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + target.tableName() + " WHERE " + target.idColumn() + " = ? " +
                "AND Financial_Transaction_ID IS NULL AND Is_Deleted = FALSE",
                Integer.class, targetRowId
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That " + target.categoryKey() + " entry doesn't exist");
        }
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That entry doesn't exist, or is already paid/assigned");
        }
    }

    // The one number the Daraja sandbox will pay out to.
    private static final String SANDBOX_TEST_MSISDN = "254708374149";

    private Map<String, Object> initiateOutgoingPayment(BigDecimal amount, String phoneNumber, ExpenseTarget target, int targetRowId,
                                                          int actorUserId, String logLabel, String remarks) {
        // Same upfront check as stkPush() - a malformed number should
        // never even get as far as a PENDING FINANCIAL_TRANSACTION row.
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            mpesaClient.validateAndNormalizePhone(phoneNumber);
        }
        // Real money: a second tap (or a second device) while a payout for this
        // same entry is still waiting on M-Pesa must not send it twice.
        Integer inFlight = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM FINANCIAL_TRANSACTION WHERE Payment_Mode = 'SEND_MONEY' AND Category = ? AND Target_ID = ? " +
            "AND Status = 'PENDING' AND Checkout_Request_ID IS NOT NULL AND Is_Deleted = FALSE " +
            "AND Initiated_At > NOW() - INTERVAL 10 MINUTE",
            Integer.class, target.name(), targetRowId
        );
        if (inFlight != null && inFlight > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A payout for this entry is already in progress - wait for M-Pesa to confirm it");
        }

        jdbcTemplate.update(
            "INSERT INTO FINANCIAL_TRANSACTION (Payment_Mode, Amount, Category, Target_ID, Created_At, Status, Initiated_At, Initiated_By) " +
            "VALUES ('SEND_MONEY', ?, ?, ?, CURDATE(), 'PENDING', NOW(), ?)",
            amount, target.name(), targetRowId, actorUserId
        );
        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        // Pre-targeted immediately (unlike incoming payments, which only
        // get Category/Target_ID once an admin assigns them) - it's
        // already resolved-in-intent, so it's excluded from the claim
        // pool from the start rather than needing assign() to do it later.

        boolean darajaInitiated = false;
        boolean redirectedToSandbox = false;
        String conversationId = null;
        String failure = null;
        if (mpesaClient.isB2CConfigured() && phoneNumber != null && !phoneNumber.isBlank()) {
            // In the sandbox a real recipient's number can't be paid, so the
            // payout goes to Safaricom's test number instead - the flow (and
            // the receipt that comes back) still works end to end, and no
            // real money moves. In production the real number is used.
            String payTo = phoneNumber;
            if (mpesaClient.isSandbox()) {
                payTo = SANDBOX_TEST_MSISDN;
                redirectedToSandbox = true;
            }
            try {
                MpesaClient.B2CResult result = mpesaClient.initiateB2C(payTo, amount, remarks, target.categoryKey());
                conversationId = result.conversationId();
                jdbcTemplate.update(
                    "UPDATE FINANCIAL_TRANSACTION SET Checkout_Request_ID = ? WHERE Financial_Transaction_ID = ?",
                    conversationId, id
                );
                darajaInitiated = true;
            } catch (Exception e) {
                log.warn("Daraja B2C send failed for Financial_Transaction_ID={}: {}", id, e.getMessage());
                failure = "M-Pesa didn't accept the payout: " + truncateReason(e.getMessage());
            }
        } else if (mpesaClient.isB2CConfigured()) {
            failure = "No recipient phone number to pay";
        }
        if (failure != null) {
            jdbcTemplate.update(
                "UPDATE FINANCIAL_TRANSACTION SET Status = 'FAILED', Status_Reason = ? WHERE Financial_Transaction_ID = ?",
                failure, id
            );
            discardFailedRepayment(target, targetRowId);
        }

        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "FINANCIAL_TRANSACTION", id,
            logLabel + " " + (darajaInitiated ? "sent via Daraja" : "recorded (Daraja not configured/unreachable)") +
                ", amount=" + amount + ", phone=" + phoneNumber + ", target=" + target.name() + "#" + targetRowId +
                (redirectedToSandbox ? " (sandbox: paid to the Safaricom test number)" : ""),
            actorUserId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("financialTransactionId", id);
        response.put("darajaInitiated", darajaInitiated);
        response.put("conversationId", conversationId);
        response.put("sandbox", redirectedToSandbox);
        response.put("failureReason", failure);
        return response;
    }

    // Pay Loan creates its repayment row before the money moves; if the payout
    // then fails that row must not stay behind as if something had been paid.
    private void discardFailedRepayment(ExpenseTarget target, int repaymentId) {
        if (target == ExpenseTarget.LOAN_REPAYMENTS) {
            jdbcTemplate.update(
                "UPDATE LOAN_REPAYMENTS SET Is_Deleted = TRUE, Deleted_At = NOW() WHERE Loan_Repayment_ID = ? AND Financial_Transaction_ID IS NULL",
                repaymentId
            );
        }
    }

    private static String truncateReason(String text) {
        if (text == null) {
            return "unknown error";
        }
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    /**
     * Where an outgoing payment stands - polled by the apps after "Send Money"
     * so nobody has to type an M-Pesa receipt in: PENDING while waiting for
     * M-Pesa's result, COMPLETED with the receipt once it lands (the B2C
     * result callback fills it), FAILED with M-Pesa's own reason otherwise.
     */
    public Map<String, Object> paymentStatus(int financialTransactionId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "SELECT Financial_Transaction_ID, Amount, Category, Target_ID, Reference_ID, Status, Status_Reason " +
                "FROM FINANCIAL_TRANSACTION WHERE Financial_Transaction_ID = ? AND Is_Deleted = FALSE",
                financialTransactionId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        String reference = (String) row.get("Reference_ID");
        String status = (String) row.get("Status");
        if (reference != null) {
            status = "COMPLETED";
        } else if (status == null) {
            status = "PENDING";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("financialTransactionId", financialTransactionId);
        result.put("status", status);
        result.put("referenceId", reference);
        result.put("amount", row.get("Amount"));
        result.put("failureReason", row.get("Status_Reason"));
        return result;
    }

    /**
     * "send money is from the company wallet and one can only send money the
     * rest is filled" - who an expense entry is to be paid to, looked up from
     * the entry itself (a farmer's phone, a salary's user, a vehicle's driver,
     * a loan's lender), plus the amount on the entry. Fields that can't be
     * worked out are left null for the person to fill in.
     */
    public Map<String, Object> sendMoneyRecipient(ExpenseTarget target, int rowId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recipientName", null);
        result.put("recipientPhone", null);
        result.put("amount", null);
        result.put("sandbox", mpesaClient.isSandbox());
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT " + target.amountColumn() + " AS Amt FROM " + target.tableName() + " WHERE " + target.idColumn() + " = ?", rowId
            );
            result.put("amount", row.get("Amt"));
        } catch (Exception ignored) {
            // amount stays null - the caller may already know it
        }
        String sql = switch (target) {
            case FARMER_PAYMENTS ->
                "SELECT f.Name AS N, f.Phone_NO AS P FROM FARMER_PAYMENTS fp JOIN FARMER f ON f.Farmer_ID = fp.Farmer_ID WHERE fp.Farmer_Payments_ID = ?";
            case SALARIES ->
                "SELECT u.Name AS N, u.Phone_NO AS P FROM SALARIES s JOIN USERS u ON u.User_ID = s.User_ID WHERE s.Salary_ID = ?";
            case TRANSPORT ->
                "SELECT v.Driver AS N, v.Phone_Number AS P FROM TRANSPORT t JOIN VEHICLE v ON v.Unique_Identifier = t.Vehicle_Unique_ID WHERE t.Transport_ID = ?";
            case LOAN_REPAYMENTS ->
                "SELECT u.Name AS N, u.Phone_NO AS P FROM LOAN_REPAYMENTS lr JOIN LOAN l ON l.Loan_ID = lr.Loan_ID JOIN USERS u ON u.User_ID = l.User_ID WHERE lr.Loan_Repayment_ID = ?";
            // Pay Loan: rowId is the LOAN itself (the repayment row only exists once it is sent).
            case LOAN ->
                "SELECT u.Name AS N, u.Phone_NO AS P FROM LOAN l JOIN USERS u ON u.User_ID = l.User_ID WHERE l.Loan_ID = ?";
            // Refunds pay back the customer who placed the order, not whoever is sending it.
            case ORDER_REFUND ->
                "SELECT u.Name AS N, u.Phone_NO AS P FROM ORDERS o JOIN USERS u ON u.User_ID = o.User_ID WHERE o.Order_ID = ?";
            default -> null;
        };
        if (sql != null) {
            try {
                Map<String, Object> who = jdbcTemplate.queryForMap(sql, rowId);
                result.put("recipientName", who.get("N"));
                result.put("recipientPhone", who.get("P"));
            } catch (Exception ignored) {
                // no linked recipient on file
            }
        }
        return result;
    }

    /**
     * Stand-in for the Safaricom Daraja B2C result callback for as long as
     * sandbox testing has no real reachable callback URL - whoever sent
     * the payment confirms it went through, with the receipt code. Since
     * Send Money always has Category/Target_ID set at creation, this
     * always auto-assigns.
     */
    @Transactional
    public Map<String, Object> confirmSendMoney(int financialTransactionId, String referenceId, int actorUserId) {
        return finalizeConfirmation(financialTransactionId, referenceId, actorUserId);
    }

    /**
     * The real Daraja B2C result callback handler (also used for the
     * QueueTimeOutURL callback - same envelope shape) - correlates by
     * ConversationID (initiateOutgoingPayment() stores it in the same
     * Checkout_Request_ID column STK uses, since a row is only ever one or
     * the other). READ_COMMITTED for the same reason as handleMpesaCallback.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleB2CResult(MpesaB2cResultRequest request) {
        MpesaB2cResultRequest.Result result = request.Result();
        String conversationId = result.ConversationID();

        Integer financialTransactionId = findFinancialTransactionIdByCheckoutRequestId(conversationId);
        if (financialTransactionId == null) {
            log.warn("Daraja B2C result for unknown ConversationID={}", conversationId);
            return;
        }

        if (result.ResultCode() != 0) {
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), NULL)",
                "FINANCIAL_TRANSACTION", financialTransactionId,
                "Send Money not completed (ResultCode=" + result.ResultCode() + "): " + result.ResultDesc()
            );
            jdbcTemplate.update(
                "UPDATE FINANCIAL_TRANSACTION SET Status = 'FAILED', Status_Reason = ? WHERE Financial_Transaction_ID = ? AND Reference_ID IS NULL",
                truncateReason("M-Pesa: " + result.ResultDesc()), financialTransactionId
            );
            Map<String, Object> failedRow = jdbcTemplate.queryForMap(
                "SELECT Category, Target_ID FROM FINANCIAL_TRANSACTION WHERE Financial_Transaction_ID = ?", financialTransactionId
            );
            if ("LOAN_REPAYMENTS".equals(failedRow.get("Category")) && failedRow.get("Target_ID") != null) {
                discardFailedRepayment(ExpenseTarget.LOAN_REPAYMENTS, ((Number) failedRow.get("Target_ID")).intValue());
            }
            return;
        }

        String transactionReceipt = result.paramValue("TransactionReceipt");
        if (transactionReceipt == null) {
            log.warn("Daraja B2C result for Financial_Transaction_ID={} succeeded but had no TransactionReceipt", financialTransactionId);
            return;
        }
        finalizeConfirmationQuietly(financialTransactionId, transactionReceipt);
    }

    // Shared by both real Daraja callback handlers - a webhook must never
    // surface an error status back to the caller, since Safaricom retries
    // on anything but a clean ack (which would just repeat forever for
    // something like a redelivered callback hitting the "already
    // confirmed" guard).
    private void finalizeConfirmationQuietly(int financialTransactionId, String referenceId) {
        try {
            finalizeConfirmation(financialTransactionId, referenceId, null);
        } catch (ResponseStatusException e) {
            log.warn("Daraja callback for Financial_Transaction_ID={} not applied: {}", financialTransactionId, e.getReason());
        }
    }

    private Map<String, Object> finalizeConfirmation(int financialTransactionId, String referenceId, Integer actorUserId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "SELECT Category, Target_ID, Reference_ID, Initiated_By FROM FINANCIAL_TRANSACTION " +
                "WHERE Financial_Transaction_ID = ? AND Is_Deleted = FALSE",
                financialTransactionId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        if (row.get("Reference_ID") != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already confirmed");
        }

        Integer duplicate = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM FINANCIAL_TRANSACTION WHERE Reference_ID = ? AND Is_Deleted = FALSE",
            Integer.class, referenceId
        );
        if (duplicate != null && duplicate > 0) {
            // Business rules: "incase the reference ids are similar then
            // they will be flagged as a double payment and cannot be filled."
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That reference ID is already recorded elsewhere - possible double payment");
        }

        jdbcTemplate.update(
            "UPDATE FINANCIAL_TRANSACTION SET Reference_ID = ?, Status = 'COMPLETED', Status_Reason = NULL WHERE Financial_Transaction_ID = ?",
            referenceId, financialTransactionId
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "FINANCIAL_TRANSACTION", financialTransactionId, "Payment confirmed, reference=" + referenceId, actorUserId
        );

        String category = (String) row.get("Category");
        Integer targetId = (Integer) row.get("Target_ID");
        if (category != null && targetId != null) {
            // M-Pesa's own callback has no signed-in user, yet recording the
            // payment (and recalculating the trip's profit) must say who did
            // it - so it is recorded as whoever sent the payout.
            Integer effectiveActor = actorUserId != null ? actorUserId : (Integer) row.get("Initiated_By");
            assign(financialTransactionId, ExpenseTarget.valueOf(category), targetId, effectiveActor);
            return Map.of("status", "auto_assigned", "financialTransactionId", financialTransactionId);
        }
        return Map.of("status", "awaiting_assignment", "financialTransactionId", financialTransactionId);
    }

    /**
     * Allocates a claimed FINANCIAL_TRANSACTION row to a specific expense
     * row: overwrites that row's Reference_ID (or unique_Salary_Id for
     * SALARIES) and Financial_Transaction_ID, then recalculates trip profit
     * if the target is tied to a single trip. Also stamps Category/Target_ID
     * back onto the FINANCIAL_TRANSACTION itself as a permanent "resolved to
     * X" marker - without it, release()'s Claimed_By=NULL made a
     * fully-assigned transaction look claimable again forever (claimBatch
     * now excludes anything with Target_ID set). actorUserId may be null
     * when invoked from the real Safaricom callback (no human actor).
     *
     * Note: FARMER_PAYMENTS is intentionally excluded from automatic profit
     * recalculation - a farmer payment can apply to more than one trip (see
     * fn_trip_farmer_payment_total's caveat), so there's no single Trip_ID
     * to recalculate against.
     */
    @Transactional
    public void assign(int financialTransactionId, ExpenseTarget target, int targetRowId, Integer actorUserId) {
        String referenceId = jdbcTemplate.queryForObject(
            "SELECT Reference_ID FROM FINANCIAL_TRANSACTION WHERE Financial_Transaction_ID = ?",
            String.class, financialTransactionId
        );

        jdbcTemplate.update(
            "UPDATE " + target.tableName() + " SET " + target.matchColumn() + " = ?, " +
            "Financial_Transaction_ID = ? WHERE " + target.idColumn() + " = ?",
            referenceId, financialTransactionId, targetRowId
        );

        jdbcTemplate.update(
            "UPDATE FINANCIAL_TRANSACTION SET Category = ?, Target_ID = ? WHERE Financial_Transaction_ID = ?",
            target.name(), targetRowId, financialTransactionId
        );

        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            target.tableName(), targetRowId,
            "Assigned Financial_Transaction_ID=" + financialTransactionId, actorUserId
        );

        Integer tripId = resolveTripId(target, targetRowId);
        if (tripId != null) {
            jdbcTemplate.update("CALL sp_recalculate_trip_profit(?, ?)", tripId, actorUserId);
        }

        release(financialTransactionId);
    }

    private Integer resolveTripId(ExpenseTarget target, int targetRowId) {
        if (target.tripIdColumn() != null) {
            return jdbcTemplate.queryForObject(
                "SELECT " + target.tripIdColumn() + " FROM " + target.tableName() +
                " WHERE " + target.idColumn() + " = ?",
                Integer.class, targetRowId
            );
        }
        if (target.tripLookupTable() != null) {
            return jdbcTemplate.queryForObject(
                "SELECT l.Trip_ID FROM " + target.tableName() + " t " +
                "JOIN " + target.tripLookupTable() + " l ON t." + target.tripLookupJoinColumn() +
                " = l." + target.tripLookupJoinColumn() +
                " WHERE t." + target.idColumn() + " = ?",
                Integer.class, targetRowId
            );
        }
        return null;
    }
}
