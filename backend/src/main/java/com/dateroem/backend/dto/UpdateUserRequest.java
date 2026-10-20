package com.dateroem.backend.dto;

// All fields optional - the desktop app's edit form only sends what
// changed; null means "leave this field as-is". canSyncSms: "only one
// phone the company phone will have the ability to read the offline SMS
// messages" - flips SmsQueueController's access flag for this account.
// userName: the SuperAdmin correcting a username typed wrongly when the
// account was made (UserService.update).
public record UpdateUserRequest(String name, String email, String phoneNo, Integer roleId, Boolean canSyncSms, String userName) {}
