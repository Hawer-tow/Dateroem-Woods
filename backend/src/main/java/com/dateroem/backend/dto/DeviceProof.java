package com.dateroem.backend.dto;

// What the mobile app sends with a sign-in to prove which phone it is on:
// its public key (X.509, base64), a one-time challenge from
// /api/auth/device/challenge and that challenge signed with the matching
// private key, which never leaves the phone's secure hardware. The rest is
// shown to the admin who approves the phone.
public record DeviceProof(
    String publicKey, String challenge, String signature, String model, String osVersion, String appVersion
) {}
