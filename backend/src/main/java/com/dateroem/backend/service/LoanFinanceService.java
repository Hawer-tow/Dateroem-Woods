package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dateroem.backend.util.JsonKeys;

@Service
public class LoanFinanceService {

    private final JdbcTemplate jdbcTemplate;

    public LoanFinanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // "how much has been paid and how much remains" - a repayment only counts
    // as PAID once it really is: a manual entry always is, but a payout sent
    // through Send Money counts only after M-Pesa confirmed it. One still
    // waiting is PENDING (shown separately), and one that failed is removed
    // when it fails (see TransactionService), so a bounced payout can never
    // shrink what's owed.
    private static final String REPAYMENT_IN_FLIGHT =
        "EXISTS (SELECT 1 FROM FINANCIAL_TRANSACTION ft WHERE ft.Category = 'LOAN_REPAYMENTS' " +
        "AND ft.Target_ID = lr.Loan_Repayment_ID AND ft.Is_Deleted = FALSE AND ft.Reference_ID IS NULL " +
        "AND ft.Status IN ('PENDING', 'FAILED'))";

    public List<Map<String, Object>> loans(int tripId) {
        List<Map<String, Object>> rows = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT l.*, " +
            "COALESCE((SELECT SUM(lr.Amount_Paid) FROM LOAN_REPAYMENTS lr WHERE lr.Loan_ID = l.Loan_ID " +
            "AND lr.Is_Deleted = FALSE AND NOT " + REPAYMENT_IN_FLIGHT + "), 0) AS Paid_Total, " +
            "COALESCE((SELECT SUM(lr.Amount_Paid) FROM LOAN_REPAYMENTS lr WHERE lr.Loan_ID = l.Loan_ID " +
            "AND lr.Is_Deleted = FALSE AND " + REPAYMENT_IN_FLIGHT + "), 0) AS Pending_Total " +
            "FROM LOAN l WHERE l.Trip_ID = ? AND l.Is_Deleted = FALSE ORDER BY l.Loan_ID", tripId
        ));
        for (Map<String, Object> loan : rows) {
            BigDecimal amount = loan.get("amount") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(loan.get("amount")));
            BigDecimal paid = new BigDecimal(String.valueOf(loan.get("paidTotal")));
            loan.put("remaining", amount.subtract(paid).max(BigDecimal.ZERO));
        }
        return rows;
    }

    public List<Map<String, Object>> capital(int tripId) {
        return JsonKeys.camelCaseKeys(
            jdbcTemplate.queryForList("SELECT * FROM CAPITAL WHERE Trip_ID = ? AND Is_Deleted = FALSE", tripId)
        );
    }

    // Each repayment says whether it is really PAID or still PENDING M-Pesa's answer.
    public List<Map<String, Object>> repayments(int loanId) {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT lr.*, CASE WHEN " + REPAYMENT_IN_FLIGHT + " THEN 'PENDING' ELSE 'PAID' END AS State " +
            "FROM LOAN_REPAYMENTS lr WHERE lr.Loan_ID = ? AND lr.Is_Deleted = FALSE ORDER BY lr.Loan_Repayment_ID", loanId
        ));
    }

    public int recordRepayment(int loanId, BigDecimal amountPaid, String description) {
        jdbcTemplate.update(
            "INSERT INTO LOAN_REPAYMENTS (Amount_Paid, Payment_Date, Description, Loan_ID) VALUES (?, CURDATE(), ?, ?)",
            amountPaid, description, loanId
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    // "allow one to access loans and see the various loans used for that
    // trip or even edit and add a new loan" - admin-only (checked by the
    // controller, same as payLoan()).
    public int createLoan(int tripId, BigDecimal amount, String description, String category, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO LOAN (Amount, Description, Category, Status, Trip_ID, User_ID) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
            amount, description, category, tripId, actorUserId
        );
        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "LOAN", id, "Created loan, amount=" + amount, actorUserId
        );
        return id;
    }

    public void updateLoan(int loanId, BigDecimal amount, String description, String category, String status, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE LOAN SET Amount = ?, Description = ?, Category = ?, Status = ? WHERE Loan_ID = ? AND Is_Deleted = FALSE",
            amount, description, category, status, loanId
        );
        if (updated == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Loan not found"
            );
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "LOAN", loanId, "Updated loan", actorUserId
        );
    }

    // "the finances one had at the start of the trip" - CAPITAL had a
    // read endpoint (capital() above, backing fn_trip_capital_total) but
    // nothing to actually create a row: mobile's Capital screen only ever
    // edited TRIP.Hard_Cash/Soft_Cash and displayed CAPITAL read-only.
    // Same create shape as createLoan().
    public int createCapital(int tripId, BigDecimal amount, String description, String category, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO CAPITAL (Amount, Description, Category, Trip_ID, Capital_From_User, Created_At) " +
            "VALUES (?, ?, ?, ?, ?, CURDATE())",
            amount, description, category, tripId, actorUserId
        );
        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "CAPITAL", id, "Created capital entry, amount=" + amount, actorUserId
        );
        return id;
    }

    // "provide an edit area to edit this ... capital" - same fields the entry
    // is created with; who put it in and when stay as they were.
    public void updateCapital(int capitalId, BigDecimal amount, String description, String category, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE CAPITAL SET Amount = ?, Description = ?, Category = ? WHERE Capital_ID = ? AND Is_Deleted = FALSE",
            amount, description, category, capitalId
        );
        if (updated == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Capital entry not found"
            );
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "CAPITAL", capitalId, "Edited capital entry, amount=" + amount, actorUserId
        );
    }
}
