package com.dateroem.backend.dto;

// Stand-in for the real Safaricom Daraja STK callback until those
// credentials exist - a user confirms their own prompt completed (they saw
// the M-Pesa SMS), providing the receipt code as referenceId.
public record ConfirmStkPushRequest(String referenceId) {}
