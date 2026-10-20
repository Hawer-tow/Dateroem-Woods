package com.dateroem.backend.dto;

import java.math.BigDecimal;

import com.dateroem.backend.model.ExpenseTarget;

// "Send Money" (Daraja B2C - Business to Customer) pays a specific already-
// recorded expense holder (business owner correction: "expenses are
// payments done to other partners ... rather a send money option", not
// STK). Unlike StkPushRequest, target/targetRowId are required - Send
// Money always pays a specific known row, there's no ambiguity to match
// later the way an unattributed SMS/STK payment has.
public record SendMoneyRequest(BigDecimal amount, String phoneNumber, ExpenseTarget target, int targetRowId) {}
