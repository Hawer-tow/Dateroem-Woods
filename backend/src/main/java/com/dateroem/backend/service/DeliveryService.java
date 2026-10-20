package com.dateroem.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.AssignLightDeliveriesBatchRequest;
import com.dateroem.backend.dto.AssignLightDeliveryRequest;
import com.dateroem.backend.dto.CreateLightDeliveryRequest;
import com.dateroem.backend.dto.FinalizeBulkDeliveryRequest;
import com.dateroem.backend.dto.SettleLightOrdersRequest;
import com.dateroem.backend.util.JsonKeys;

@Service
public class DeliveryService {

    private final JdbcTemplate jdbcTemplate;
    private final TripService tripService;

    public DeliveryService(JdbcTemplate jdbcTemplate, TripService tripService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tripService = tripService;
    }

    // tripId is optional: unfiltered for admin-side "all deliveries" views,
    // filtered for the mobile trips page ("see how many light and heavy
    // deliveries associated to it").
    public List<Map<String, Object>> lightDeliveries(Integer tripId) {
        // Order/area context alongside each row, so a delivery that carries
        // several orders can show what's actually in it (which order, how
        // much, heading where) instead of bare ids.
        // "in deliver i have the received by by default write the name of
        // the one who ordered" - Customer_Name backs that default on the
        // mobile deliver-confirmation dialog.
        String select =
            "SELECT ld.*, o.Total, o.Created_At AS Ordered_At, da.Area_Name, da.City AS Delivery_City, u.Name AS Customer_Name " +
            "FROM LIGHT_DELIVERIES ld " +
            "LEFT JOIN ORDERS o ON o.Order_ID = ld.Order_ID " +
            "LEFT JOIN DELIVERY_AREAS da ON da.Delivery_Area_ID = o.Delivery_Area_ID " +
            "LEFT JOIN USERS u ON u.User_ID = o.User_ID ";
        if (tripId != null) {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
                select + "WHERE ld.Trip_ID = ? AND ld.Is_Deleted = FALSE ORDER BY ld.Light_Delivery_ID", tripId
            ));
        }
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            select + "WHERE ld.Is_Deleted = FALSE ORDER BY ld.Light_Delivery_ID"
        ));
    }

    public void updateLightDeliveryStatus(int lightDeliveryId, String status) {
        jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES SET Status = ?, " +
            "Delivered_At = CASE WHEN ? = 'DELIVERED' THEN CURDATE() ELSE Delivered_At END " +
            "WHERE Light_Delivery_ID = ?",
            status, status, lightDeliveryId
        );
    }

    /**
     * "in mobile when one is delivering a product i want to add a
     * signature area which is stored in the signature blob for the said
     * products for the person to be added to the database" -
     * Signature_Blub already existed on the schema (widened from
     * varchar(250) to mediumtext to actually hold a drawn signature's data
     * URL) but nothing ever wrote to it; this is that write path. Both
     * fields are optional - a delivery can still be marked DELIVERED
     * without one, same as before this existed, just without proof of
     * receipt on file.
     */
    public void deliverLightDelivery(int lightDeliveryId, String signatureDataUrl, String receivedBy) {
        int updated = jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES SET Status = 'DELIVERED', Delivered_At = COALESCE(Delivered_At, CURDATE()), " +
            "Signature_Blub = ?, Received_By = ? WHERE Light_Delivery_ID = ? AND Is_Deleted = FALSE",
            (signatureDataUrl == null || signatureDataUrl.isBlank()) ? null : signatureDataUrl,
            (receivedBy == null || receivedBy.isBlank()) ? null : receivedBy,
            lightDeliveryId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found");
        }
    }

    // "the admin assigns a delivery ... if it's heading to the same
    // direction and there is space" - pending orders not yet attached to
    // an outgoing trip/vehicle, with enough order/area context (Order_ID,
    // area/city, the generic SLA estimate already on file) for an admin
    // to judge which ones fit a trip they're about to send out.
    public List<Map<String, Object>> unassignedLightDeliveries() {
        // Oldest order first (by the orders' own primary key - the order
        // they were actually placed in), so the desktop can number them
        // 1..N as a fair pick order. Needs_Reassignment marks orders that
        // missed a delivery, for the UI to highlight as high priority.
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT ld.Light_Delivery_ID, ld.Order_ID, ld.Status, ld.Estimated_Delivery_Date, " +
            "ld.Needs_Reassignment, ld.Undelivered_Reason, " +
            "o.Total, o.Created_At, da.Area_Name, da.City AS Delivery_City, u.Name AS Customer, " +
            "(SELECT GROUP_CONCAT(CONCAT(p.Name, ' x', oi.Qty) SEPARATOR ', ') FROM ORDER_ITEMS oi " +
            " JOIN PRODUCTS p ON p.Product_ID = oi.Product_ID " +
            " WHERE oi.Order_ID = o.Order_ID AND oi.Is_Deleted = FALSE) AS Items " +
            "FROM LIGHT_DELIVERIES ld " +
            "JOIN ORDERS o ON o.Order_ID = ld.Order_ID AND o.Is_Deleted = FALSE " +
            "LEFT JOIN DELIVERY_AREAS da ON da.Delivery_Area_ID = o.Delivery_Area_ID " +
            "LEFT JOIN USERS u ON u.User_ID = o.User_ID " +
            "WHERE ld.Trip_ID IS NULL AND ld.Status = 'PENDING' AND ld.Is_Deleted = FALSE " +
            "ORDER BY o.Order_ID"
        ));
    }

    // The M-Pesa receipt of the order's completed website payment (migration
    // 035), for the Orders lists - "i need to see this". Ends with a space so
    // it can be followed straight by FROM.
    private static final String PAID_RECEIPT_COLUMN =
        "(SELECT pp.Mpesa_Receipt FROM PRODUCT_PAYMENTS pp WHERE pp.Order_ID = o.Order_ID AND pp.Status = 'COMPLETED' " +
        "AND pp.Is_Deleted = FALSE ORDER BY pp.Product_Payment_ID DESC LIMIT 1) AS Mpesa_Receipt ";

    // Per-unit size above which an item makes its whole order "large" (a bulk
    // candidate) - the same 500 body-feet rule OrderService uses for the
    // customer-facing 3-month vs 3-week estimate.
    private static final java.math.BigDecimal LARGE_ITEM_BODY_FEET = java.math.BigDecimal.valueOf(500);

    /**
     * "in the unassigned orders some of these orders may be from the same
     * person so how do we collect them in a single group so that an admin
     * can gauge the goods and then check if there is a bulk order or light
     * or combined" - the pending pool, folded into one group per customer
     * per delivery area (oldest order first, so groups keep a stable 1..N
     * order). Each group carries its orders with their items, the total
     * body feet, and a LIGHT / BULK / COMBINED verdict: BULK when every
     * order holds a large item, LIGHT when none does, COMBINED when mixed.
     */
    public List<Map<String, Object>> pendingOrderGroups() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT ld.Light_Delivery_ID, ld.Order_ID, ld.Needs_Reassignment, ld.Undelivered_Reason, " +
            "o.Total, o.Created_At, o.User_ID, o.Delivery_Area_ID, da.Area_Name, da.City, " +
            "u.Name AS Customer, u.Phone_NO AS Phone, " + PAID_RECEIPT_COLUMN +
            "FROM LIGHT_DELIVERIES ld " +
            "JOIN ORDERS o ON o.Order_ID = ld.Order_ID AND o.Is_Deleted = FALSE " +
            "LEFT JOIN DELIVERY_AREAS da ON da.Delivery_Area_ID = o.Delivery_Area_ID " +
            "LEFT JOIN USERS u ON u.User_ID = o.User_ID " +
            "WHERE ld.Trip_ID IS NULL AND ld.Status = 'PENDING' AND ld.Is_Deleted = FALSE " +
            "ORDER BY o.Order_ID"
        );

        Map<Integer, List<Map<String, Object>>> itemsByOrder = new java.util.HashMap<>();
        for (Map<String, Object> item : jdbcTemplate.queryForList(
            "SELECT oi.Order_ID, p.Name, oi.Qty, p.Body_Feet_Per_Unit FROM ORDER_ITEMS oi " +
            "JOIN PRODUCTS p ON p.Product_ID = oi.Product_ID WHERE oi.Is_Deleted = FALSE AND oi.Order_ID IN (" +
            "SELECT ld.Order_ID FROM LIGHT_DELIVERIES ld WHERE ld.Trip_ID IS NULL AND ld.Status = 'PENDING' AND ld.Is_Deleted = FALSE)"
        )) {
            itemsByOrder.computeIfAbsent(((Number) item.get("Order_ID")).intValue(), k -> new java.util.ArrayList<>()).add(item);
        }

        Map<String, Map<String, Object>> groups = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            int orderId = ((Number) row.get("Order_ID")).intValue();
            Object userId = row.get("User_ID");
            Object areaId = row.get("Delivery_Area_ID");
            String key = userId + "|" + areaId;

            java.math.BigDecimal orderBodyFeet = java.math.BigDecimal.ZERO;
            boolean large = false;
            boolean unmeasured = false;
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            List<String> itemNames = new java.util.ArrayList<>();
            for (Map<String, Object> item : itemsByOrder.getOrDefault(orderId, List.of())) {
                java.math.BigDecimal qty = new java.math.BigDecimal(String.valueOf(item.get("Qty")));
                java.math.BigDecimal perUnit = (java.math.BigDecimal) item.get("Body_Feet_Per_Unit");
                Map<String, Object> shaped = new java.util.LinkedHashMap<>();
                shaped.put("name", item.get("Name"));
                shaped.put("qty", qty);
                if (perUnit == null) {
                    unmeasured = true;
                    shaped.put("bodyFeet", null);
                } else {
                    shaped.put("bodyFeet", perUnit.multiply(qty));
                    orderBodyFeet = orderBodyFeet.add(perUnit.multiply(qty));
                    if (perUnit.compareTo(LARGE_ITEM_BODY_FEET) > 0) {
                        large = true;
                    }
                }
                items.add(shaped);
                itemNames.add(item.get("Name") + " x" + qty.stripTrailingZeros().toPlainString());
            }

            Map<String, Object> order = new java.util.LinkedHashMap<>();
            order.put("lightDeliveryId", row.get("Light_Delivery_ID"));
            order.put("orderId", orderId);
            order.put("total", row.get("Total"));
            order.put("orderedAt", row.get("Created_At"));
            order.put("items", items);
            order.put("itemsText", String.join(", ", itemNames));
            order.put("bodyFeet", orderBodyFeet);
            order.put("large", large);
            order.put("unmeasured", unmeasured);
            order.put("needsReassignment", Boolean.TRUE.equals(row.get("Needs_Reassignment")) || Long.valueOf(1).equals(row.get("Needs_Reassignment")));
            order.put("undeliveredReason", row.get("Undelivered_Reason"));
            order.put("mpesaReceipt", row.get("Mpesa_Receipt"));

            Map<String, Object> group = groups.get(key);
            if (group == null) {
                group = new java.util.LinkedHashMap<>();
                group.put("customerId", userId);
                group.put("customer", row.get("Customer"));
                group.put("phone", row.get("Phone"));
                group.put("areaId", areaId);
                String city = (String) row.get("City");
                String areaName = (String) row.get("Area_Name");
                group.put("area", ((city == null ? "" : city) + " - " + (areaName == null ? "" : areaName)).replaceAll("^ - | - $", ""));
                group.put("orders", new java.util.ArrayList<Map<String, Object>>());
                groups.put(key, group);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupOrders = (List<Map<String, Object>>) group.get("orders");
            groupOrders.add(order);
        }

        // Roll each group's orders up into the numbers an admin decides on.
        for (Map<String, Object> group : groups.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> orders = (List<Map<String, Object>>) group.get("orders");
            long total = 0;
            java.math.BigDecimal bodyFeet = java.math.BigDecimal.ZERO;
            int largeOrders = 0;
            boolean priority = false;
            boolean unmeasured = false;
            List<String> itemsText = new java.util.ArrayList<>();
            for (Map<String, Object> order : orders) {
                total += order.get("total") == null ? 0 : ((Number) order.get("total")).longValue();
                bodyFeet = bodyFeet.add((java.math.BigDecimal) order.get("bodyFeet"));
                if (Boolean.TRUE.equals(order.get("large"))) {
                    largeOrders++;
                }
                priority |= Boolean.TRUE.equals(order.get("needsReassignment"));
                unmeasured |= Boolean.TRUE.equals(order.get("unmeasured"));
                itemsText.add((String) order.get("itemsText"));
            }
            group.put("orderCount", orders.size());
            group.put("total", total);
            group.put("bodyFeet", bodyFeet);
            group.put("largeOrders", largeOrders);
            group.put("kind", largeOrders == 0 ? "LIGHT" : (largeOrders == orders.size() ? "BULK" : "COMBINED"));
            group.put("needsReassignment", priority);
            group.put("unmeasured", unmeasured);
            group.put("itemsText", String.join(" | ", itemsText));
            group.put("firstOrderId", orders.get(0).get("orderId"));
        }
        attachAssignedBatches(groups);
        return new java.util.ArrayList<>(groups.values());
    }

    /**
     * "if a new order comes from that user ... the unassigned will go below
     * the assigned batch and if one can still add it in the bunch if the
     * staff deems worth it" - alongside each pending group, the same
     * customer's orders (same area) that are ALREADY on a delivery, folded
     * into their batches (a bulk delivery's side quests, or the orders
     * grouped under one light delivery). The UI lists these first, with the
     * new unassigned orders underneath and an "add to this batch" action.
     */
    private void attachAssignedBatches(Map<String, Map<String, Object>> groups) {
        java.util.Set<String> userIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> group : groups.values()) {
            if (group.get("customerId") != null) {
                userIds.add(String.valueOf(((Number) group.get("customerId")).intValue()));
            }
        }
        Map<String, Map<String, Map<String, Object>>> batchesByGroup = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            List<Map<String, Object>> assigned = jdbcTemplate.queryForList(
                "SELECT ld.Light_Delivery_ID, ld.Order_ID, ld.Trip_ID, ld.Vehicle_ID, ld.Estimated_Delivery_Date, " +
                "ld.Bulk_Delivery_ID, ld.Parent_Light_Delivery_ID, o.Total, o.User_ID, o.Delivery_Area_ID, " +
                "v.Unique_Identifier AS Vehicle, " + PAID_RECEIPT_COLUMN +
                "FROM LIGHT_DELIVERIES ld " +
                "JOIN ORDERS o ON o.Order_ID = ld.Order_ID AND o.Is_Deleted = FALSE " +
                "LEFT JOIN VEHICLE v ON v.Vehicle_ID = ld.Vehicle_ID " +
                "WHERE ld.Trip_ID IS NOT NULL AND ld.Status <> 'DELIVERED' AND ld.Is_Deleted = FALSE " +
                "AND o.Total > 0 AND o.User_ID IN (" + String.join(",", userIds) + ") ORDER BY o.Order_ID"
            );
            Map<Integer, List<String>> itemTextByOrder = new java.util.HashMap<>();
            if (!assigned.isEmpty()) {
                String orderIds = assigned.stream().map(r -> String.valueOf(((Number) r.get("Order_ID")).intValue()))
                    .collect(java.util.stream.Collectors.joining(","));
                for (Map<String, Object> item : jdbcTemplate.queryForList(
                    "SELECT oi.Order_ID, p.Name, oi.Qty FROM ORDER_ITEMS oi JOIN PRODUCTS p ON p.Product_ID = oi.Product_ID " +
                    "WHERE oi.Is_Deleted = FALSE AND oi.Order_ID IN (" + orderIds + ")"
                )) {
                    java.math.BigDecimal qty = new java.math.BigDecimal(String.valueOf(item.get("Qty")));
                    itemTextByOrder.computeIfAbsent(((Number) item.get("Order_ID")).intValue(), k -> new java.util.ArrayList<>())
                        .add(item.get("Name") + " x" + qty.stripTrailingZeros().toPlainString());
                }
            }
            for (Map<String, Object> row : assigned) {
                String groupKey = row.get("User_ID") + "|" + row.get("Delivery_Area_ID");
                if (!groups.containsKey(groupKey)) {
                    continue;
                }
                boolean bulk = row.get("Bulk_Delivery_ID") != null;
                Object parent = row.get("Parent_Light_Delivery_ID");
                Object targetId = bulk ? row.get("Bulk_Delivery_ID") : (parent != null ? parent : row.get("Light_Delivery_ID"));
                String batchKey = (bulk ? "b" : "l") + targetId;

                Map<String, Map<String, Object>> batches = batchesByGroup.computeIfAbsent(groupKey, k -> new java.util.LinkedHashMap<>());
                Map<String, Object> batch = batches.get(batchKey);
                if (batch == null) {
                    batch = new java.util.LinkedHashMap<>();
                    batch.put("kind", bulk ? "bulk" : "light");
                    batch.put("targetId", targetId);
                    batch.put("tripId", row.get("Trip_ID"));
                    batch.put("vehicleId", row.get("Vehicle_ID"));
                    batch.put("vehicle", row.get("Vehicle"));
                    batch.put("date", row.get("Estimated_Delivery_Date"));
                    batch.put("orders", new java.util.ArrayList<Map<String, Object>>());
                    batches.put(batchKey, batch);
                }
                int orderId = ((Number) row.get("Order_ID")).intValue();
                Map<String, Object> order = new java.util.LinkedHashMap<>();
                order.put("orderId", orderId);
                order.put("total", row.get("Total"));
                order.put("itemsText", String.join(", ", itemTextByOrder.getOrDefault(orderId, List.of())));
                order.put("mpesaReceipt", row.get("Mpesa_Receipt"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> batchOrders = (List<Map<String, Object>>) batch.get("orders");
                batchOrders.add(order);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : groups.entrySet()) {
            Map<String, Map<String, Object>> batches = batchesByGroup.getOrDefault(entry.getKey(), Map.of());
            int assignedCount = 0;
            for (Map<String, Object> batch : batches.values()) {
                assignedCount += ((List<?>) batch.get("orders")).size();
            }
            entry.getValue().put("assignedBatches", new java.util.ArrayList<>(batches.values()));
            entry.getValue().put("assignedCount", assignedCount);
        }
    }

    // The whole group (or the orders the admin left ticked) into one delivery.
    @Transactional
    public void assignLightDeliveriesBatch(AssignLightDeliveriesBatchRequest batch, int actorUserId) {
        if (batch.lightDeliveryIds() == null || batch.lightDeliveryIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one order");
        }
        AssignLightDeliveryRequest single = new AssignLightDeliveryRequest(
            batch.tripId(), batch.vehicleId(), batch.estimatedDeliveryDate(),
            batch.bulkDeliveryId(), batch.parentLightDeliveryId()
        );
        for (int id : batch.lightDeliveryIds()) {
            assignLightDelivery(id, single, actorUserId);
        }
    }

    // Links the order's delivery to a real outgoing trip/vehicle and
    // gives it a concrete date - from this point on, the customer's
    // "ordered goods" view shows this date instead of the generic
    // 3-week/3-month SLA estimate set at checkout, "even if normally for
    // light goods it can take up to 3 weeks".
    @Transactional
    public void assignLightDelivery(int lightDeliveryId, AssignLightDeliveryRequest request, int actorUserId) {
        Integer tripId = request.tripId();
        Integer vehicleId = request.vehicleId();
        java.time.LocalDate date = request.estimatedDeliveryDate();
        Integer bulkDeliveryId = request.bulkDeliveryId();
        Integer parentId = request.parentLightDeliveryId();
        String target = "trip " + tripId;

        if (bulkDeliveryId != null) {
            // "a light delivery can be fixed to a bulk delivery as a side
            // quest" - it rides on the bulk delivery's own trip.
            Map<String, Object> bulk;
            try {
                bulk = jdbcTemplate.queryForMap(
                    "SELECT Trip_ID, Delivered_At FROM BULK_DELIVERY WHERE Bulk_Delivery_ID = ? AND Is_Deleted = FALSE",
                    bulkDeliveryId
                );
            } catch (EmptyResultDataAccessException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk delivery not found");
            }
            // "if a delivery was completed ... dont allow it to be assigned
            // new orders ... you either create a new one or use one still
            // active" - a delivered bulk run has already gone out.
            if (bulk.get("Delivered_At") != null) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "That bulk delivery is already completed - create a new one or use one still active"
                );
            }
            tripId = (Integer) bulk.get("Trip_ID");
            target = "bulk delivery " + bulkDeliveryId + " (side quest)";
        } else if (parentId != null) {
            if (parentId == lightDeliveryId) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery can't be grouped under itself");
            }
            Map<String, Object> parent;
            try {
                parent = jdbcTemplate.queryForMap(
                    "SELECT Trip_ID, Vehicle_ID, Estimated_Delivery_Date, Status FROM LIGHT_DELIVERIES " +
                    "WHERE Light_Delivery_ID = ? AND Is_Deleted = FALSE", parentId
                );
            } catch (EmptyResultDataAccessException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent light delivery not found");
            }
            if ("DELIVERED".equals(parent.get("Status"))) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "That delivery is already completed - create a new one or use one still active"
                );
            }
            tripId = (Integer) parent.get("Trip_ID");
            if (vehicleId == null) {
                vehicleId = (Integer) parent.get("Vehicle_ID");
            }
            if (date == null && parent.get("Estimated_Delivery_Date") != null) {
                date = ((java.sql.Date) parent.get("Estimated_Delivery_Date")).toLocalDate();
            }
            target = "light delivery " + parentId;
        }
        if (tripId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The delivery to assign to has no trip");
        }

        int updated = jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES SET Trip_ID = ?, Vehicle_ID = ?, Estimated_Delivery_Date = ?, " +
            "Bulk_Delivery_ID = ?, Parent_Light_Delivery_ID = ?, Needs_Reassignment = FALSE, Undelivered_Reason = NULL " +
            "WHERE Light_Delivery_ID = ? AND Is_Deleted = FALSE",
            tripId, vehicleId, date, bulkDeliveryId, parentId, lightDeliveryId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Light delivery not found");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "LIGHT_DELIVERIES", lightDeliveryId,
            "Assigned to " + target + ", delivery date " + date,
            actorUserId
        );
    }

    // "when a delivery is being marked as delivered any product that was not
    // delivered for one or more issues ... will be dropped for reassignment
    // in another delivery ... highlighted ... high priority" - settles the
    // orders riding on a delivery: the delivered ones are closed out, the
    // rest are cut loose from the trip/vehicle/date and go back to the
    // unassigned pool flagged Needs_Reassignment (with the reason), where
    // the desktop shows them highlighted for the next delivery.
    @Transactional
    public void settleLightOrders(SettleLightOrdersRequest request, int actorUserId) {
        List<Integer> delivered = request.deliveredIds() == null ? List.of() : request.deliveredIds();
        List<Integer> undelivered = request.undeliveredIds() == null ? List.of() : request.undeliveredIds();
        for (int id : delivered) {
            jdbcTemplate.update(
                "UPDATE LIGHT_DELIVERIES SET Status = 'DELIVERED', Delivered_At = CURDATE(), " +
                "Needs_Reassignment = FALSE, Undelivered_Reason = NULL WHERE Light_Delivery_ID = ? AND Is_Deleted = FALSE", id
            );
        }
        for (int id : undelivered) {
            jdbcTemplate.update(
                "UPDATE LIGHT_DELIVERIES SET Status = 'PENDING', Trip_ID = NULL, Vehicle_ID = NULL, " +
                "Estimated_Delivery_Date = NULL, Bulk_Delivery_ID = NULL, Parent_Light_Delivery_ID = NULL, " +
                "Needs_Reassignment = TRUE, Undelivered_Reason = ? WHERE Light_Delivery_ID = ? AND Is_Deleted = FALSE",
                request.reason(), id
            );
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
                "LIGHT_DELIVERIES", id, "Not delivered - back to pool for reassignment: " + request.reason(), actorUserId
            );
        }
    }

    // Mirrors createBulkOrder: a light delivery opened straight on a trip
    // needs its own ORDERS row (LIGHT_DELIVERIES.Order_ID is how every
    // other delivery is tied to one), owned by whoever created it.
    @Transactional
    public Map<String, Object> createLightDelivery(CreateLightDeliveryRequest request, int actorUserId) {
        // Same guard as createBulkOrder - a stale/nonexistent trip id would
        // otherwise fail on the Trip_ID foreign key as a raw 500.
        Integer tripExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TRIP WHERE Trip_ID = ? AND Is_Deleted = FALSE", Integer.class, request.tripId()
        );
        if (tripExists == null || tripExists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        // "i dont seem to have the destination written in it and the
        // logic is that we make decisions based on areas of delivery so
        // we need this so we know if we are adding it to a bunch or
        // creating another delivery" - the desktop's own "New Light
        // Delivery" form used to let this go through with no area picked
        // at all (confirmed live: three real orders ended up with a null
        // Delivery_Area_ID that way); enforced here too so no caller,
        // desktop or otherwise, can skip it again.
        if (request.deliveryAreaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick the delivery area this is heading to");
        }
        jdbcTemplate.update(
            "INSERT INTO ORDERS (Total, Status, Created_At, Delivery_Area_ID, User_ID) VALUES (0, 'PENDING', CURDATE(), ?, ?)",
            request.deliveryAreaId(), actorUserId
        );
        int orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO LIGHT_DELIVERIES (Status, Delivery_Name, Order_ID, Trip_ID, Vehicle_ID, Estimated_Delivery_Date) " +
            "VALUES ('PENDING', ?, ?, ?, ?, ?)",
            blankToNull(request.deliveryName()), orderId, request.tripId(), request.vehicleId(), request.estimatedDeliveryDate()
        );
        int lightDeliveryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "LIGHT_DELIVERIES", lightDeliveryId, "Light delivery opened on trip " + request.tripId(), actorUserId
        );
        return Map.of("orderId", orderId, "lightDeliveryId", lightDeliveryId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public List<Map<String, Object>> bulkDeliveries(Integer tripId) {
        if (tripId != null) {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
                "SELECT * FROM BULK_DELIVERY WHERE Trip_ID = ? AND Is_Deleted = FALSE", tripId
            ));
        }
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList("SELECT * FROM BULK_DELIVERY WHERE Is_Deleted = FALSE"));
    }

    public Map<String, Object> getBulkDelivery(int bulkDeliveryId) {
        try {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
                "SELECT * FROM BULK_DELIVERY WHERE Bulk_Delivery_ID = ? AND Is_Deleted = FALSE", bulkDeliveryId
            ));
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk order not found");
        }
    }

    public List<Map<String, Object>> bulkProductionDescriptions(int bulkDeliveryId) {
        // Explicit columns (no Photo) - the image is fetched separately via
        // productionPhoto(); the list only says whether one exists.
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT Bulk_Trip_Production_Description_ID, Product_Type, Product_Description, Product_Measurement, " +
            "Total_Body_Feet, Product_Total, Bulk_Delivery_ID, Trip_ID, (Photo IS NOT NULL) AS Has_Photo " +
            "FROM BULK_TRIP_PRODUCTION_DESCRIPTION WHERE Bulk_Delivery_ID = ? AND Is_Deleted = FALSE " +
            "ORDER BY Bulk_Trip_Production_Description_ID", bulkDeliveryId
        ));
    }

    public String productionPhoto(int descriptionId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT Photo FROM BULK_TRIP_PRODUCTION_DESCRIPTION " +
                "WHERE Bulk_Trip_Production_Description_ID = ? AND Is_Deleted = FALSE AND Photo IS NOT NULL",
                String.class, descriptionId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo on this entry");
        }
    }

    // "in bulk goods for each in a trip add a BULK_TRIP_PRODUCTION_DESCRIPTION
    // section to add data concerning that BULK_DELIVERY" - finalizeBulkDelivery()
    // below already writes exactly one of these rows as part of closing out a
    // delivery, but a bulk order can carry more than one product-description
    // line item (several dimension/measurement entries), so this adds an
    // extra row independent of finalize instead of only ever getting one.
    public int createBulkProductionDescription(
        int bulkDeliveryId, String productType, String productDescription, String productMeasurement,
        java.math.BigDecimal totalBodyFeet, java.math.BigDecimal productTotal, String photo
    ) {
        int tripId;
        try {
            tripId = jdbcTemplate.queryForObject(
                "SELECT Trip_ID FROM BULK_DELIVERY WHERE Bulk_Delivery_ID = ? AND Is_Deleted = FALSE",
                Integer.class, bulkDeliveryId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk order not found");
        }
        jdbcTemplate.update(
            "INSERT INTO BULK_TRIP_PRODUCTION_DESCRIPTION " +
            "(Product_Type, Product_Description, Product_Measurement, Total_Body_Feet, Product_Total, Bulk_Delivery_ID, Trip_ID, Photo) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            productType, productDescription, productMeasurement, totalBodyFeet, productTotal, bulkDeliveryId, tripId, photo
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
    }

    /**
     * "the heavy delivery orders search and finalization ... i want to
     * allow the admin or staff to create a bulk order and its added into
     * the system" - creates a paired ORDERS (the negotiation record) +
     * BULK_DELIVERY (the trip-linked fulfillment record) row, mirroring
     * LIGHT_DELIVERIES' own Order_ID linkage. Status starts PENDING;
     * finalizeBulkDelivery() marks it delivered.
     */
    @Transactional
    public Map<String, Object> createBulkOrder(String deliveredTo, int tripId, int actorUserId) {
        // "creating heavy order error http 500" - a stale/nonexistent trip
        // id (e.g. left over on a device from before a data reset) let the
        // insert fall all the way to the DB and fail on the Trip_ID
        // foreign key, surfacing as a raw 500 instead of a real message.
        Integer tripExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TRIP WHERE Trip_ID = ? AND Is_Deleted = FALSE", Integer.class, tripId
        );
        if (tripExists == null || tripExists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        }
        jdbcTemplate.update(
            "INSERT INTO ORDERS (Total, Status, Created_At, User_ID) VALUES (0, 'PENDING', CURDATE(), ?)",
            actorUserId
        );
        int orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        jdbcTemplate.update(
            "INSERT INTO BULK_DELIVERY (Delivered_To, Created_At, Order_ID, Trip_ID) VALUES (?, NOW(), ?, ?)",
            deliveredTo, orderId, tripId
        );
        int bulkDeliveryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "BULK_DELIVERY", bulkDeliveryId, "Heavy order opened for " + deliveredTo, actorUserId
        );

        return Map.of("orderId", orderId, "bulkDeliveryId", bulkDeliveryId);
    }

    /**
     * "if it fails its just deleted" - a heavy-order negotiation that
     * didn't pan out. Soft-deletes both halves of the pair together.
     */
    @Transactional
    public void deleteBulkOrder(int bulkDeliveryId, int actorUserId) {
        Integer orderId;
        try {
            orderId = jdbcTemplate.queryForObject(
                "SELECT Order_ID FROM BULK_DELIVERY WHERE Bulk_Delivery_ID = ? AND Is_Deleted = FALSE",
                Integer.class, bulkDeliveryId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk order not found");
        }

        // Side quests riding with this bulk delivery would otherwise be left
        // pointing at a deleted delivery and vanish from every list - they go
        // back to the pending pool (no priority: nothing went wrong with them).
        jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES SET Status = 'PENDING', Trip_ID = NULL, Vehicle_ID = NULL, " +
            "Estimated_Delivery_Date = NULL, Bulk_Delivery_ID = NULL, Parent_Light_Delivery_ID = NULL, " +
            "Needs_Reassignment = FALSE, Undelivered_Reason = NULL " +
            "WHERE Bulk_Delivery_ID = ? AND Status <> 'DELIVERED' AND Is_Deleted = FALSE",
            bulkDeliveryId
        );
        jdbcTemplate.update(
            "UPDATE BULK_DELIVERY SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Bulk_Delivery_ID = ?",
            actorUserId, bulkDeliveryId
        );
        if (orderId != null) {
            jdbcTemplate.update(
                "UPDATE ORDERS SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Order_ID = ?",
                actorUserId, orderId
            );
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "BULK_DELIVERY", bulkDeliveryId, "Heavy order deleted (negotiation did not proceed)", actorUserId
        );
    }

    /**
     * "once the bulk delivery is delivered the details are filled on
     * amount cash soft which is filled into the bulk delivery as well as
     * the bulk delivery description table ... the total in bulk delivery
     * is written all soft and hard". Amount_Paid is the combined total;
     * the soft portion (Soft_Cash_Amount) is what STK-push candidate-
     * matching later assigns a real payment against (see ExpenseTarget.
     * BULK_DELIVERY). Also recalculates the trip's Total_Payment, closing
     * the loop described: "how total amount paid in bulk delivery +
     * product payment amount ... the total amount in trip is calculated."
     *
     * Re-callable on purpose - "provide a place to allow one to edit since
     * one might have missed on some issues": calling this again on an
     * already-delivered order overwrites the hard/soft cash instead of
     * failing or piling up a second row. Delivered_At is only ever set
     * once (COALESCE), and the product-description row is only inserted
     * when productType was actually filled in, so a pure payment
     * correction doesn't create a duplicate line item.
     */
    @Transactional
    public void finalizeBulkDelivery(int bulkDeliveryId, FinalizeBulkDeliveryRequest request, int actorUserId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "SELECT Trip_ID, Order_ID FROM BULK_DELIVERY WHERE Bulk_Delivery_ID = ? AND Is_Deleted = FALSE",
                bulkDeliveryId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk order not found");
        }
        Integer tripId = (Integer) row.get("Trip_ID");
        Integer orderId = (Integer) row.get("Order_ID");

        var hardCash = request.hardCashAmount() == null ? java.math.BigDecimal.ZERO : request.hardCashAmount();
        var softCash = request.softCashAmount() == null ? java.math.BigDecimal.ZERO : request.softCashAmount();

        jdbcTemplate.update(
            "UPDATE BULK_DELIVERY SET Delivered_At = COALESCE(Delivered_At, CURDATE()), Hard_Cash_Amount = ?, " +
            "Soft_Cash_Amount = ?, Amount_Paid = ? WHERE Bulk_Delivery_ID = ?",
            hardCash, softCash, hardCash.add(softCash), bulkDeliveryId
        );

        boolean hasProductionEntry = request.productType() != null && !request.productType().isBlank();
        if (hasProductionEntry) {
            jdbcTemplate.update(
                "INSERT INTO BULK_TRIP_PRODUCTION_DESCRIPTION " +
                "(Product_Type, Product_Description, Product_Measurement, Total_Body_Feet, Product_Total, Bulk_Delivery_ID, Trip_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                request.productType(), request.productDescription(), request.productMeasurement(),
                request.totalBodyFeet(), request.productTotal(), bulkDeliveryId, tripId
            );
        }

        if (orderId != null) {
            jdbcTemplate.update("UPDATE ORDERS SET Status = 'DELIVERED' WHERE Order_ID = ?", orderId);
        }

        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "BULK_DELIVERY", bulkDeliveryId,
            "Payment recorded, hard=" + hardCash + " soft=" + softCash, actorUserId
        );

        if (tripId != null) {
            tripService.recalculateTotalPayment(tripId);
        }
    }

    // Correcting a mistyped line item after the fact - same fields as
    // createBulkProductionDescription, applied to an existing row instead
    // of inserting a new one. Product_Total doesn't feed the trip's
    // Amount_Paid/profit (see finalizeBulkDelivery), so no recalculation
    // is needed here.
    public void updateBulkProductionDescription(
        int descriptionId, String productType, String productDescription, String productMeasurement,
        java.math.BigDecimal totalBodyFeet, java.math.BigDecimal productTotal, String photoDataUrl
    ) {
        String photo = photoDataUrl == null || photoDataUrl.isBlank() ? null : photoDataUrl;
        int updated = photo != null
            ? jdbcTemplate.update(
                "UPDATE BULK_TRIP_PRODUCTION_DESCRIPTION SET Product_Type = ?, Product_Description = ?, " +
                "Product_Measurement = ?, Total_Body_Feet = ?, Product_Total = ?, Photo = ? " +
                "WHERE Bulk_Trip_Production_Description_ID = ? AND Is_Deleted = FALSE",
                productType, productDescription, productMeasurement, totalBodyFeet, productTotal, photo, descriptionId
            )
            : jdbcTemplate.update(
                "UPDATE BULK_TRIP_PRODUCTION_DESCRIPTION SET Product_Type = ?, Product_Description = ?, " +
                "Product_Measurement = ?, Total_Body_Feet = ?, Product_Total = ? " +
                "WHERE Bulk_Trip_Production_Description_ID = ? AND Is_Deleted = FALSE",
                productType, productDescription, productMeasurement, totalBodyFeet, productTotal, descriptionId
            );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Production description entry not found");
        }
    }

    public void deleteBulkProductionDescription(int descriptionId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE BULK_TRIP_PRODUCTION_DESCRIPTION SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE Bulk_Trip_Production_Description_ID = ? AND Is_Deleted = FALSE",
            actorUserId, descriptionId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Production description entry not found");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "BULK_TRIP_PRODUCTION_DESCRIPTION", descriptionId, "Entry deleted (recorded by mistake)", actorUserId
        );
    }
}
