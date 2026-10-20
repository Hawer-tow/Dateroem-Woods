package com.dateroem.backend.dto;

// A phone number to send a confirmation code to, or the number and the code
// typed back (PhoneVerificationService).
public record PhoneCodeRequest(String phoneNo, String code) {}
