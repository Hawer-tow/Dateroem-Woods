package com.dateroem.backend.dto;

import java.util.List;

// userId is intentionally absent: it comes from the caller's validated JWT
// (see OrderController), never from client-supplied JSON, so a customer
// can't place an order as someone else. hotspotCity is likewise gone -
// OrderService now derives it server-side from the chosen, real
// DELIVERY_AREAS row instead of trusting a free-typed city string.
// phoneNumber is optional - the M-Pesa number to send the STK push to;
// checkout still works without one, just without a payment prompt.
public record CreateOrderRequest(Integer deliveryAreaId, List<OrderItemRequest> items, String phoneNumber) {}
