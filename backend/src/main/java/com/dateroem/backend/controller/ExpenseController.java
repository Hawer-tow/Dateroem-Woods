package com.dateroem.backend.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.dto.CreateExpenseRequest;
import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.service.ExpenseService;
import com.dateroem.backend.service.RoleService;

@RestController
@RequestMapping("/api/trips/{tripId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final RoleService roleService;

    public ExpenseController(ExpenseService expenseService, RoleService roleService) {
        this.expenseService = expenseService;
        this.roleService = roleService;
    }

    @GetMapping
    public Map<String, BigDecimal> totals(@PathVariable int tripId) {
        return expenseService.categoryTotals(tripId);
    }

    @GetMapping("/{category}")
    public List<Map<String, Object>> lineItems(
        @PathVariable int tripId,
        @PathVariable String category,
        @RequestParam(defaultValue = "all") String assigned
    ) {
        return expenseService.lineItems(tripId, category, assigned);
    }

    @PostMapping("/{category}")
    public Map<String, Object> create(
        @PathVariable int tripId,
        @PathVariable String category,
        @RequestBody CreateExpenseRequest request,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        return expenseService.create(category, tripId, request, actorUserId);
    }

    @DeleteMapping("/{category}/{rowId}")
    public void delete(
        @PathVariable int tripId,
        @PathVariable String category,
        @PathVariable int rowId,
        @RequestHeader("X-Actor-User-Id") int actorUserId
    ) {
        expenseService.delete(category, rowId, actorUserId, roleService.isAdmin(actorUserId));
    }
}
