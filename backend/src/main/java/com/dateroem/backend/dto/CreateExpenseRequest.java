package com.dateroem.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Generic across the 8 mobile "create an expense holder" categories
// (transport, communications, machine-expenses, accomodation,
// other-expenses, permits, salaries, farmer-payments) - fields only one or
// two categories actually use (vehicleUniqueId, additionalId, machineId,
// serialNo, startDate/endDate, farmerId, method) are simply ignored by the
// categories that don't have a column for them. additionalId is
// TRANSPORT's fallback plate/number entry - "if its not in the vehicles
// then one can type the field additional id ... the other id is only
// filled if the vehicle used was not in the vehicles section" - used only
// when no VEHICLE record was selected. Reference_ID/
// Financial_Transaction_ID are deliberately absent: a holder is created
// unassigned by design, business rules: "stored in the system but not yet
// validated ... later validated by admins."
public record CreateExpenseRequest(
    BigDecimal amount,
    String description,
    String type,
    String vehicleUniqueId,
    String additionalId,
    Integer machineId,
    String serialNo,
    LocalDate startDate,
    LocalDate endDate,
    Integer farmerId,
    // "any tree created in this a certain trip will be linked to the
    // farmer then to the payment" - FARMER_PAYMENTS only, same as
    // farmerId; which specific tree registration this payment is for.
    Integer treesId,
    // "connect major expense to a user not just who created it ... who
    // its meant for" - every category except FARMER_PAYMENTS/PERMITS;
    // ignored (no column) for the ones that don't apply.
    Integer forUserId,
    String method
) {}
