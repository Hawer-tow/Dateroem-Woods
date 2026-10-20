package com.dateroem.backend.dto;

import com.dateroem.backend.model.ExpenseTarget;

public record AssignTransactionRequest(ExpenseTarget target, int targetRowId) {}
