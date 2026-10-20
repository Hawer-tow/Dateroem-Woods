package com.dateroem.backend.dto;

// phoneProof: from /api/auth/phone/verify - shows the phone number was
// confirmed by an SMS code (needed while SMS is set up, see
// PhoneVerificationService).
public record RegisterRequest(String userName, String name, String password, String email, String phoneNo, String phoneProof) {}
