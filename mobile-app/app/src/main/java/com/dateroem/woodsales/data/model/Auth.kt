package com.dateroem.woodsales.data.model

data class LoginRequest(
    val user_name: String,
    val password: String,
    // Proof of which phone this is (DeviceIdentity) - the server only signs
    // in an approved phone: "allows only one user per account per mobile phone".
    val device: DeviceProof? = null
)

data class DeviceProof(
    val publicKey: String,
    val challenge: String,
    val signature: String,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?
)

data class DeviceChallenge(
    val challenge: String,
    val expiresInSeconds: Int = 120
)

data class AuthResponse(
    val token: String,
    val userId: Int,
    val userName: String,
    val name: String,
    val roleId: Int,
    val roleName: String,
    // "only one phone the company phone will have the ability to read
    // the offline SMS messages" - whether this account is the one
    // designated to sync SMS; gates whether that screen even shows up.
    val canSyncSms: Boolean = false,
    // The account's colour theme - "forest", "dark" or "light" (Settings).
    val theme: String? = null
)
