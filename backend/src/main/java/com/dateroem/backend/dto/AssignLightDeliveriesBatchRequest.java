package com.dateroem.backend.dto;

import java.time.LocalDate;
import java.util.List;

// One customer's group of pending orders sent to a single delivery in one go
// (all of them, or just the ones the admin left ticked). Same targeting rules
// as AssignLightDeliveryRequest: a bulk delivery (side quests), a light
// delivery to group under, or just a trip.
public record AssignLightDeliveriesBatchRequest(
    List<Integer> lightDeliveryIds, Integer tripId, Integer vehicleId, LocalDate estimatedDeliveryDate,
    Integer bulkDeliveryId, Integer parentLightDeliveryId, Boolean shareDriver
) {}
