package com.dateroem.backend.dto;

import java.math.BigDecimal;

// "once the bulk delivery is delivered the details are filled on amount
// cash soft which is filled into the bulk delivery as well as the bulk
// delivery description table" - hardCashAmount/softCashAmount are the two
// payment components (business owner: "the hardcash part is written
// manually ... but the total in bulk delivery is written all soft and
// hard"); the product* fields go into BULK_TRIP_PRODUCTION_DESCRIPTION.
public record FinalizeBulkDeliveryRequest(
    BigDecimal hardCashAmount,
    BigDecimal softCashAmount,
    String productType,
    String productDescription,
    String productMeasurement,
    BigDecimal totalBodyFeet,
    BigDecimal productTotal
) {}
