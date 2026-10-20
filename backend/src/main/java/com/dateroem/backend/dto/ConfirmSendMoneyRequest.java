package com.dateroem.backend.dto;

// Stand-in for the real Daraja B2C result callback until those credentials
// exist - mirrors ConfirmStkPushRequest but for outgoing payments.
public record ConfirmSendMoneyRequest(String referenceId) {}
