package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.TripSummaryDto;
import com.dateroem.backend.util.JsonKeys;

@Service
public class TripService {

    private final JdbcTemplate jdbcTemplate;

    public TripService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Most-recent-first list - mobileServer wraps this with its own
    // adaptive batch sizing (business rules: "send only a maximum of data
    // related to 3-6 trips ... if loaded even 2 or 1 trip data can be sent
    // to reduce load in traffic times"), always passing an explicit limit.
    // limit=null means "all" (same convention as Product/Campaign/User) -
    // "desktop one should see all the trips ... not unless i select the
    // 50 only etc". Ordered by Trip_ID rather than Start_Date - "i believe
    // the ids will help since they are integers", a plain auto-increment
    // is a more reliable "latest" than a business date a trip's own
    // Start_Date could in principle be backdated to.
    public List<Map<String, Object>> list(Integer limit) {
        String sql = "SELECT * FROM TRIP WHERE Is_Deleted = FALSE ORDER BY Trip_ID DESC";
        List<Map<String, Object>> rows = limit == null
            ? jdbcTemplate.queryForList(sql)
            : jdbcTemplate.queryForList(sql + " LIMIT ?", limit);
        return JsonKeys.camelCaseKeys(rows);
    }

    // "having error http 500 even after restarting the servers" - this
    // threw a raw, unhandled EmptyResultDataAccessException (-> a generic
    // 500) for any trip id that doesn't exist, instead of a clean 404.
    // Surfaced right after the data reset: every trip was wiped, so a
    // phone/desktop still holding an old "active trip" id from before
    // that now hits exactly this on every screen that resolves it.
    public Map<String, Object> get(int tripId) {
        try {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
                "SELECT * FROM TRIP WHERE Trip_ID = ? AND Is_Deleted = FALSE", tripId
            ));
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
    }

    // TRIP is the main entity everything else connects to - starts with
    // just a start date and description; deliveries/payments/expenses get
    // attached to it afterward, and Total_Payment/profit are computed later.
    public int create(LocalDate startDate, String productDescription) {
        // "you can never create a new trip before you finalize every detail in
        // the other trip - deliveries, loans ... all those must be finalized
        // first" - one open trip at a time; a trip is open until it has been
        // through finalizeTrip().
        Map<String, Object> open = openTrip();
        if (open != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Trip #" + open.get("tripId") + " is still open. Finalize it first - every order delivered or dropped, "
                + "its loans settled and its profit recorded - before starting a new trip."
            );
        }
        jdbcTemplate.update(
            "INSERT INTO TRIP (Start_Date, NO_of_Deliveries, Total_Payment, Hard_Cash, Soft_Cash, " +
            "Product_Description, Total_Body_Feet) VALUES (?, 0, 0, 0, 0, ?, 0)",
            startDate, productDescription
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    public void updateDetails(int tripId, LocalDate endDate, Integer noOfDeliveries,
                               BigDecimal totalBodyFeet, String productDescription) {
        // An open trip is closed only by finalizeTrip() (which checks
        // deliveries, loans and profit) - setting an end date here would be
        // a way round all of that. A finalized trip keeps its end date when
        // an edit doesn't supply one.
        Map<String, Object> trip = get(tripId);
        if (trip.get("endDate") == null && endDate != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "A trip is closed with Finalize Trip, not by setting an end date"
            );
        }
        jdbcTemplate.update(
            "UPDATE TRIP SET End_Date = COALESCE(?, End_Date), NO_of_Deliveries = ?, Total_Body_Feet = ?, Product_Description = ? " +
            "WHERE Trip_ID = ?",
            endDate, noOfDeliveries, totalBodyFeet, productDescription, tripId
        );
    }

    // The one trip that has not been finalized yet, if any.
    public Map<String, Object> openTrip() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM TRIP WHERE End_Date IS NULL AND Is_Deleted = FALSE ORDER BY Trip_ID DESC LIMIT 1"
        );
        return rows.isEmpty() ? null : JsonKeys.camelCaseKeys(rows.get(0));
    }

    /**
     * What still stands between this trip and being finalized: orders not
     * delivered (these can be dropped back to the pending list), bulk
     * deliveries not finished (must be delivered or deleted), and loans still
     * ACTIVE (must be paid or written off).
     */
    public Map<String, Object> finalizationCheck(int tripId) {
        Map<String, Object> trip = get(tripId);
        List<Map<String, Object>> pendingOrders = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT ld.Light_Delivery_ID, ld.Order_ID, ld.Status, ld.Bulk_Delivery_ID, ld.Parent_Light_Delivery_ID, " +
            "o.Total, da.City AS Delivery_City, da.Area_Name " +
            "FROM LIGHT_DELIVERIES ld JOIN ORDERS o ON o.Order_ID = ld.Order_ID AND o.Is_Deleted = FALSE " +
            "LEFT JOIN DELIVERY_AREAS da ON da.Delivery_Area_ID = o.Delivery_Area_ID " +
            "WHERE ld.Trip_ID = ? AND ld.Is_Deleted = FALSE AND ld.Status NOT IN ('DELIVERED', 'CANCELLED') AND o.Total > 0 " +
            "ORDER BY ld.Light_Delivery_ID", tripId
        ));
        List<Map<String, Object>> pendingBulk = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT Bulk_Delivery_ID, Delivered_To FROM BULK_DELIVERY " +
            "WHERE Trip_ID = ? AND Is_Deleted = FALSE AND Delivered_At IS NULL ORDER BY Bulk_Delivery_ID", tripId
        ));
        List<Map<String, Object>> activeLoans = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT Loan_ID, Amount, Description FROM LOAN WHERE Trip_ID = ? AND Is_Deleted = FALSE AND Status = 'ACTIVE' " +
            "ORDER BY Loan_ID", tripId
        ));
        boolean open = trip.get("endDate") == null;
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("open", open);
        result.put("pendingOrders", pendingOrders);
        result.put("pendingBulkDeliveries", pendingBulk);
        result.put("activeLoans", activeLoans);
        // Orders alone don't block - they can be dropped back to the pending
        // list; bulk deliveries and loans must be dealt with explicitly.
        result.put("blocked", !pendingBulk.isEmpty() || !activeLoans.isEmpty());
        result.put("canFinalize", open && pendingBulk.isEmpty() && activeLoans.isEmpty());
        return result;
    }

    /**
     * "all the orders during that trip should either be delivered or if
     * could not be possible dropped from those deliveries and are taken to
     * the next trip ... all those must be finalized first" - closes the
     * trip: orders still undelivered (with dropUndelivered) go back to the
     * pending list flagged as priority so the next trip picks them up
     * first, delivery containers are closed, the trip's payment total and
     * profit are recalculated, and the end date is set.
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> finalizeTrip(int tripId, boolean dropUndelivered, int actorUserId) {
        Map<String, Object> check = finalizationCheck(tripId);
        if (!Boolean.TRUE.equals(check.get("open"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "This trip is already finalized"
            );
        }
        List<?> loans = (List<?>) check.get("activeLoans");
        if (!loans.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                loans.size() + " loan(s) are still active on this trip - mark them Paid or Written off first"
            );
        }
        List<?> bulk = (List<?>) check.get("pendingBulkDeliveries");
        if (!bulk.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                bulk.size() + " bulk deliver" + (bulk.size() == 1 ? "y is" : "ies are")
                + " not finished - finalize or delete " + (bulk.size() == 1 ? "it" : "them") + " first"
            );
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pendingOrders = (List<Map<String, Object>>) check.get("pendingOrders");
        if (!pendingOrders.isEmpty() && !dropUndelivered) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                pendingOrders.size() + " order(s) were not delivered - deliver them, or choose to drop them to the next trip"
            );
        }

        for (Map<String, Object> order : pendingOrders) {
            int lightId = ((Number) order.get("lightDeliveryId")).intValue();
            jdbcTemplate.update(
                "UPDATE LIGHT_DELIVERIES SET Status = 'PENDING', Trip_ID = NULL, Vehicle_ID = NULL, " +
                "Estimated_Delivery_Date = NULL, Bulk_Delivery_ID = NULL, Parent_Light_Delivery_ID = NULL, " +
                "Needs_Reassignment = TRUE, Undelivered_Reason = ? WHERE Light_Delivery_ID = ?",
                "Trip #" + tripId + " ended before this was delivered", lightId
            );
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
                "LIGHT_DELIVERIES", lightId, "Dropped at trip finalization - back to the pending list as priority", actorUserId
            );
        }
        // Delivery containers (their own placeholder order, Total 0) have
        // nothing left to deliver once the trip is over - close them.
        jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES ld JOIN ORDERS o ON o.Order_ID = ld.Order_ID SET ld.Status = 'DELIVERED', " +
            "ld.Delivered_At = COALESCE(ld.Delivered_At, CURDATE()) " +
            "WHERE ld.Trip_ID = ? AND ld.Is_Deleted = FALSE AND ld.Status = 'PENDING' AND COALESCE(o.Total, 0) = 0",
            tripId
        );

        recalculateTotalPayment(tripId);
        jdbcTemplate.update("CALL sp_recalculate_trip_profit(?, ?)", tripId, actorUserId);
        jdbcTemplate.update("UPDATE TRIP SET End_Date = CURDATE() WHERE Trip_ID = ?", tripId);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "TRIP", tripId, "Trip finalized; " + pendingOrders.size() + " undelivered order(s) dropped to the pending list", actorUserId
        );

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tripId", tripId);
        result.put("droppedOrders", pendingOrders.size());
        result.put("profit", getSummary(tripId).profit());
        return result;
    }

    // business rule: TRIP.Total_Payment = PRODUCT_PAYMENTS(Trip_ID).amount + BULK_DELIVERY(Trip_ID).Amount_Paid
    public BigDecimal recalculateTotalPayment(int tripId) {
        BigDecimal total = jdbcTemplate.queryForObject(
            "SELECT fn_trip_product_payment_total(?) + fn_trip_bulk_delivery_total(?)",
            BigDecimal.class, tripId, tripId
        );
        jdbcTemplate.update("UPDATE TRIP SET Total_Payment = ? WHERE Trip_ID = ?", total, tripId);
        return total;
    }

    // business rule: "the soft cash and the cash will be filled by providing
    // an option for the user to fill the amount of cash paid in hard or soft cash"
    public void recordCashSplit(int tripId, BigDecimal hardCash, BigDecimal softCash) {
        jdbcTemplate.update(
            "UPDATE TRIP SET Hard_Cash = ?, Soft_Cash = ? WHERE Trip_ID = ?",
            hardCash, softCash, tripId
        );
    }

    public TripSummaryDto getSummary(int tripId) {
        // Same "expected 1, actual 0" crash as get() above, for the same
        // reason (a stale trip id from before the data reset) - every
        // trip-scoped screen calls this, so it was likely the single
        // biggest source of the 500s reported after the reset.
        BigDecimal totalPayment;
        try {
            totalPayment = jdbcTemplate.queryForObject(
                "SELECT Total_Payment FROM TRIP WHERE Trip_ID = ? AND Is_Deleted = FALSE", BigDecimal.class, tripId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }

        // Real units sold, traced from each product back to the tree
        // record (and trip) it was actually cut from - not a count of
        // PRODUCT_PAYMENTS rows, which only ever covers trip-scoped bulk
        // orders and is blind to website orders entirely (those write
        // INVENTORY_MOVEMENT but never a trip-scoped payment row). Changes
        // is a signed-string column ("-3"), negative for a sale - CAST +
        // negate turns it back into a positive quantity.
        Integer productsSold = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(-CAST(im.Changes AS SIGNED)), 0) " +
            "FROM INVENTORY_MOVEMENT im " +
            "JOIN PRODUCTS p ON p.Product_ID = im.Product_ID " +
            "JOIN TREES t ON t.Trees_ID = p.Trees_ID " +
            "WHERE t.Trip_ID = ? AND im.Reason = 'ORDER' AND im.Is_Deleted = FALSE",
            Integer.class, tripId
        );

        return new TripSummaryDto(
            tripId,
            totalPayment,
            fnScalar("fn_trip_profit", tripId),
            fnScalar("fn_trip_loan_total", tripId),
            fnScalar("fn_trip_capital_total", tripId),
            fnScalar("fn_trip_farmer_payment_total", tripId),
            fnScalar("fn_trip_salary_total", tripId),
            fnScalar("fn_trip_accomodation_total", tripId),
            fnScalar("fn_trip_transport_total", tripId),
            fnScalar("fn_trip_communication_total", tripId),
            fnScalar("fn_trip_machine_expense_total", tripId),
            fnScalar("fn_trip_other_expense_total", tripId),
            fnScalar("fn_trip_permit_total", tripId),
            productsSold
        );
    }

    private BigDecimal fnScalar(String functionName, int tripId) {
        return jdbcTemplate.queryForObject("SELECT " + functionName + "(?)", BigDecimal.class, tripId);
    }

    /**
     * "a trip can be deleted and this will force all the assigned orders
     * back to the unassigned ones but this time not given priority since
     * the delivery may have been made by mistake" - soft-deletes the trip.
     * Every order still riding on it (grouped under a light delivery, a
     * bulk side quest, or assigned straight to the trip) drops back to the
     * pending pool with its trip/vehicle/date cleared and NO priority flag,
     * unlike an order that was genuinely attempted and missed. Delivery
     * containers opened on the trip (their own placeholder order, Total 0)
     * have nothing to return, so they are removed. Orders already
     * delivered keep their record.
     */
    @org.springframework.transaction.annotation.Transactional
    public void delete(int tripId, int actorUserId) {
        get(tripId); // 404s a missing/already-deleted trip

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT ld.Light_Delivery_ID, ld.Order_ID, ld.Status, COALESCE(o.Total, 0) AS Total " +
            "FROM LIGHT_DELIVERIES ld LEFT JOIN ORDERS o ON o.Order_ID = ld.Order_ID " +
            "WHERE ld.Trip_ID = ? AND ld.Is_Deleted = FALSE", tripId
        );
        for (Map<String, Object> row : rows) {
            int lightId = ((Number) row.get("Light_Delivery_ID")).intValue();
            boolean delivered = "DELIVERED".equals(row.get("Status"));
            boolean container = ((Number) row.get("Total")).intValue() == 0;
            if (delivered) {
                continue;
            }
            if (container) {
                jdbcTemplate.update(
                    "UPDATE LIGHT_DELIVERIES SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Light_Delivery_ID = ?",
                    actorUserId, lightId
                );
                if (row.get("Order_ID") != null) {
                    jdbcTemplate.update(
                        "UPDATE ORDERS SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Order_ID = ?",
                        actorUserId, ((Number) row.get("Order_ID")).intValue()
                    );
                }
            } else {
                jdbcTemplate.update(
                    "UPDATE LIGHT_DELIVERIES SET Status = 'PENDING', Trip_ID = NULL, Vehicle_ID = NULL, " +
                    "Estimated_Delivery_Date = NULL, Bulk_Delivery_ID = NULL, Parent_Light_Delivery_ID = NULL, " +
                    "Needs_Reassignment = FALSE, Undelivered_Reason = NULL WHERE Light_Delivery_ID = ?",
                    lightId
                );
            }
        }

        jdbcTemplate.update(
            "UPDATE TRIP SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Trip_ID = ?",
            actorUserId, tripId
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "TRIP", tripId, "Trip deleted; its undelivered orders returned to the pending list", actorUserId
        );
    }
}
