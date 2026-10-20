package com.dateroem.backend.dto;

import java.math.BigDecimal;

public record TripSummaryDto(
    Integer tripId,
    BigDecimal totalPayment,
    BigDecimal profit,
    BigDecimal loanTotal,
    BigDecimal capitalTotal,
    BigDecimal farmerPaymentTotal,
    BigDecimal salaryTotal,
    BigDecimal accomodationTotal,
    BigDecimal transportTotal,
    BigDecimal communicationTotal,
    BigDecimal machineExpenseTotal,
    BigDecimal otherExpenseTotal,
    BigDecimal permitTotal,
    // "products bought should not be 0 ... a product is linked to a
    // treeid which is linked to a trip ... products bought during that
    // trip then should be registered as sold" - real summed quantities
    // (INVENTORY_MOVEMENT) traced through each product's own tree/trip,
    // not a count of PRODUCT_PAYMENTS rows (the old productPaymentsCount,
    // which only ever counted trip-scoped bulk-order payments and never
    // saw a website order - see TripService.getSummary()).
    Integer productsSold
) {}
