package com.dateroem.backend.dto;

public record CreateUserRequest(String userName, String name, String password, String email, String phoneNo, int roleId) {}
