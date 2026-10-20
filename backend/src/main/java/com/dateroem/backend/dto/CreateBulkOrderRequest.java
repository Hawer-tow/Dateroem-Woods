package com.dateroem.backend.dto;

// "the heavy delivery orders search and finalization ... i want to allow
// the admin or staff to create a bulk order and its added into the system
// and if it fails its just deleted" - creates a paired ORDERS + BULK_DELIVERY
// row (mirrors LIGHT_DELIVERIES' Order_ID linkage), Status='PENDING' until
// finalizeBulkDelivery() marks it delivered.
public record CreateBulkOrderRequest(String deliveredTo, int tripId) {}
