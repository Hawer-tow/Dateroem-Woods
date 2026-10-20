package com.dateroem.backend.dto;

import java.util.List;

// LIGHT_DELIVERIES ids that were delivered vs. ones that weren't (and why).
public record SettleLightOrdersRequest(List<Integer> deliveredIds, List<Integer> undeliveredIds, String reason) {}
