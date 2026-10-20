package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dateroem.backend.service.ExpenseService;

// Cross-cutting insight endpoints that don't naturally scope to one trip
// or belong to a single existing controller - "an ai feature that allows
// one to perform some of the simplifications of the jobs ... provide
// insights". Starts small (unassigned transactions live on
// TransactionController, top farmer suppliers on FarmerController - both
// existing, single-entity homes); this is where the ones that don't fit
// anywhere else go.
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final ExpenseService expenseService;

    public InsightsController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/expenses/breakdown")
    public List<Map<String, Object>> expenseBreakdown() {
        return expenseService.expenseBreakdown();
    }
}
