package com.dateroem.backend.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.service.LoanFinanceService;
import com.dateroem.backend.service.RoleService;
import com.dateroem.backend.service.TransactionService;

@RestController
@RequestMapping("/api")
public class LoanFinanceController {

    private final LoanFinanceService loanFinanceService;
    private final TransactionService transactionService;
    private final RoleService roleService;

    public LoanFinanceController(LoanFinanceService loanFinanceService, TransactionService transactionService, RoleService roleService) {
        this.loanFinanceService = loanFinanceService;
        this.transactionService = transactionService;
        this.roleService = roleService;
    }

    @GetMapping("/trips/{tripId}/finance/loans")
    public List<Map<String, Object>> loans(@PathVariable int tripId) {
        return loanFinanceService.loans(tripId);
    }

    // "allow one to access loans ... or even edit and add a new loan" -
    // admin-only, same check as payLoan() below (Loans & Finance is
    // "only of the admin/Superadmins" per business rules).
    @PostMapping("/trips/{tripId}/finance/loans")
    public Map<String, Object> createLoan(@PathVariable int tripId, @RequestBody LoanRequest body,
                                           @RequestHeader("X-Actor-User-Id") int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        int id = loanFinanceService.createLoan(tripId, body.amount(), body.description(), body.category(), actorUserId);
        return Map.of("loanId", id);
    }

    @PatchMapping("/loans/{loanId}")
    public void updateLoan(@PathVariable int loanId, @RequestBody UpdateLoanRequest body,
                            @RequestHeader("X-Actor-User-Id") int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        loanFinanceService.updateLoan(loanId, body.amount(), body.description(), body.category(), body.status(), actorUserId);
    }

    @GetMapping("/trips/{tripId}/finance/capital")
    public List<Map<String, Object>> capital(@PathVariable int tripId) {
        return loanFinanceService.capital(tripId);
    }

    // "add the finances one had at the start of the trip" - admin-only,
    // same guard as createLoan() (Loans & Finance is admin/SuperAdmin only).
    @PostMapping("/trips/{tripId}/finance/capital")
    public Map<String, Object> createCapital(@PathVariable int tripId, @RequestBody CapitalRequest body,
                                              @RequestHeader("X-Actor-User-Id") int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        int id = loanFinanceService.createCapital(tripId, body.amount(), body.description(), body.category(), actorUserId);
        return Map.of("capitalId", id);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/finance/capital/{capitalId}")
    public void updateCapital(@PathVariable int capitalId, @RequestBody CapitalRequest body,
                               @RequestHeader("X-Actor-User-Id") int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        loanFinanceService.updateCapital(capitalId, body.amount(), body.description(), body.category(), actorUserId);
    }

    @GetMapping("/loans/{loanId}/repayments")
    public List<Map<String, Object>> repayments(@PathVariable int loanId) {
        return loanFinanceService.repayments(loanId);
    }

    @PostMapping("/loans/{loanId}/repayments")
    public Integer recordRepayment(@PathVariable int loanId, @RequestBody RepaymentRequest body) {
        return loanFinanceService.recordRepayment(loanId, body.amountPaid(), body.description());
    }

    // "for each loan there will be an option to click it and an option pay
    // loan in it ... led to the transactions area and the option loan
    // repayment for the selected loan" - admin-only (Loans & Finance is
    // "only of the admin/Superadmins" per business rules), initiates a
    // real Send Money payout for a fresh repayment on this loan.
    @PostMapping("/loans/{loanId}/repayments/pay")
    public Map<String, Object> payLoan(@PathVariable int loanId, @RequestBody PayLoanRequest body,
                                        @RequestHeader("X-Actor-User-Id") int actorUserId) {
        if (!roleService.isAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin/SuperAdmin only");
        }
        return transactionService.payLoan(loanId, body.amount(), body.phoneNumber(), actorUserId);
    }

    public record RepaymentRequest(BigDecimal amountPaid, String description) {}

    public record PayLoanRequest(BigDecimal amount, String phoneNumber) {}

    public record LoanRequest(BigDecimal amount, String description, String category) {}

    public record CapitalRequest(BigDecimal amount, String description, String category) {}

    public record UpdateLoanRequest(BigDecimal amount, String description, String category, String status) {}
}
