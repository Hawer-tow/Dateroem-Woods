package com.dateroem.backend.dto;

import java.math.BigDecimal;

// treesId is optional - "any tree created in this a certain trip will be
// linked to the farmer then to the payment" - which specific tree
// registration this payment is compensating for, so a farmer with
// several registrations (possibly across trips) has each payment
// traceable to the right one, not just to the farmer in general.
public record CreateFarmerPaymentRequest(
    int farmerId, Integer treesId, BigDecimal amount, String method, String description
) {}
