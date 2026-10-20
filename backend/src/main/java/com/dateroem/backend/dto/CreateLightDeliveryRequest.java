package com.dateroem.backend.dto;

import java.time.LocalDate;

// "in desktop in both deliveries allow one to create one" - opens a light
// delivery directly on a trip (its own paired ORDERS row, like
// createBulkOrder), which pending customer orders can then be assigned
// under.
// deliveryName - "allow one to name a delivery when assigning a new one
// ... a name delivery maasai allows quick identification" - optional,
// purely cosmetic alongside the auto-generated Light_Delivery_ID.
public record CreateLightDeliveryRequest(int tripId, Integer vehicleId, Integer deliveryAreaId, LocalDate estimatedDeliveryDate, String deliveryName) {}
