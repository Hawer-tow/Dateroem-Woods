package com.dateroem.backend.dto;

import java.time.LocalDate;

// "the admin assigns a delivery e.g to a light delivery or a bulk if it's
// heading to the same direction and there is space ... the assigned
// delivery since it has a delivery end the customer in the ordered
// goods sees will be delivered on 21st" - links a pending order's
// LIGHT_DELIVERIES row to a real outgoing delivery and gives it a
// concrete date, overriding the generic 3-week/3-month SLA estimate
// that was set at checkout.
//
// Exactly one target: bulkDeliveryId (a "side quest" riding along with
// that bulk delivery), parentLightDeliveryId (grouped under another light
// delivery), or - the older whole-trip form - just tripId. When a bulk or
// parent delivery is named, the trip (and, for a parent, the vehicle and
// date when left blank) come from that delivery itself.
public record AssignLightDeliveryRequest(
    Integer tripId, Integer vehicleId, LocalDate estimatedDeliveryDate,
    Integer bulkDeliveryId, Integer parentLightDeliveryId
) {}
