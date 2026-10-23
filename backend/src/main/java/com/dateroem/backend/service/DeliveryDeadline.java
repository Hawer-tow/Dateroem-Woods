package com.dateroem.backend.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * "if an order has not been assigned yet and the time is near let it be
 * treated as a risky product since it can lead to complaints ... so the
 * admins can assign it to a delivery before deadline date or just on
 * deadline date" (24 October 2026).
 *
 * Every order is promised a date when it is placed (3 weeks, or 3 months for
 * large items - OrderService). An order still waiting for a delivery is
 * DUE_SOON in the last quarter of that time, and never later than 5 days
 * before the date - so a 3-week order is flagged 5 days ahead and a 3-month
 * one about 3 weeks ahead, when bulk work needs the lead time - and LATE once
 * the date the customer sees has passed.
 */
public final class DeliveryDeadline {

    public static final String DUE_SOON = "DUE_SOON";
    public static final String LATE = "LATE";
    private static final long MIN_WARNING_DAYS = 5;

    private DeliveryDeadline() {}

    /** daysLeft (negative when late) and risk (DUE_SOON, LATE or null); empty without a promised date. */
    public static Map<String, Object> of(Object orderedOn, Object promisedOn, LocalDate today) {
        LocalDate promised = date(promisedOn);
        if (promised == null) {
            return Map.of();
        }
        LocalDate ordered = date(orderedOn);
        long daysLeft = ChronoUnit.DAYS.between(today, promised);
        long window = ordered == null ? 21 : Math.max(1, ChronoUnit.DAYS.between(ordered, promised));
        long warnAt = Math.max(MIN_WARNING_DAYS, (window + 3) / 4);
        String risk = daysLeft < 0 ? LATE : daysLeft <= warnAt ? DUE_SOON : null;
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("promisedOn", promised.toString());
        result.put("daysLeft", daysLeft);
        result.put("deadlineRisk", risk);
        return result;
    }

    /** The worse of two risks (LATE over DUE_SOON over none). */
    public static String worse(String a, String b) {
        if (LATE.equals(a) || LATE.equals(b)) {
            return LATE;
        }
        return DUE_SOON.equals(a) || DUE_SOON.equals(b) ? DUE_SOON : null;
    }

    private static LocalDate date(Object value) {
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime t) {
            return t.toLocalDate();
        }
        return value == null ? null : LocalDate.parse(value.toString().substring(0, 10));
    }
}
