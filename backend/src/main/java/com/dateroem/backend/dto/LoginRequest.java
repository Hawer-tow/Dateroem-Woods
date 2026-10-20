package com.dateroem.backend.dto;

// device is sent only by the mobile app, computer only by the desktop app
// (both null for the website).
public record LoginRequest(String userName, String password, DeviceProof device, DeviceProof computer) {}
