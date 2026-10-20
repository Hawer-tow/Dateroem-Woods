package com.dateroem.backend.dto;

import java.math.BigDecimal;

public record FarmerDto(Integer farmerId, String name, String phoneNo, String bankDetailsMarked, BigDecimal balance) {}
