package com.dateroem.backend.dto;

// canSyncSms: "only one phone the company phone will have the ability to
// read the offline SMS messages" - whether this account is the one
// designated to submit M-Pesa SMS sync data, so the mobile app can hide
// that screen entirely for everyone else instead of just hoping nobody
// else opens it.
// theme: the account's chosen colour theme - forest (the default), dark or
// light (USERS.Theme, migration 037) - so every app opens in it.
public record AuthResponse(
    String token, int userId, String userName, String name, int roleId, String roleName, boolean canSyncSms,
    String theme
) {}
