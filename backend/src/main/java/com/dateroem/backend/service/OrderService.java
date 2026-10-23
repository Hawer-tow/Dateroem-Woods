package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CreateOrderRequest;
import com.dateroem.backend.dto.CreateOrderResult;
import com.dateroem.backend.dto.OrderItemRequest;
import com.dateroem.backend.util.JsonKeys;
import com.dateroem.backend.util.MediaUrls;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Payment_Status comes from the light order's own STK push attempt, if
    // any (PRODUCT_PAYMENTS.Order_ID) - null when phoneNumber was never
    // given or Daraja isn't configured, same as before this feature existed.
    //
    // "vehicle visible only to staff" (migration 038) - the driver's first
    // name and the number plate come out only for a delivery staff switched
    // Share driver on for; otherwise both are null, and the vehicle's
    // internal id never leaves at all.
    private static final String ORDER_WITH_DELIVERY_STATUS =
        "SELECT o.Order_ID, o.Ticket_Code, o.Total, o.Status, o.Created_At, o.Hotspot_City, o.User_ID, " +
        "da.Area_Name, da.City AS Delivery_City, " +
        "ld.Status AS Delivery_Status, ld.Delivered_At, ld.Estimated_Delivery_Date, " +
        "CASE WHEN ld.Share_Driver THEN v.Unique_Identifier END AS Vehicle_Identifier, " +
        "CASE WHEN ld.Share_Driver THEN NULLIF(SUBSTRING_INDEX(TRIM(v.Driver), ' ', 1), '') END AS Driver_First_Name, " +
        // A yes/no rather than the Trip_ID itself - the website only needs
        // to know "an admin has put this on a real delivery run", and
        // internal ids stay off the customer's screen.
        "(ld.Trip_ID IS NOT NULL) AS Is_Scheduled, ld.Needs_Reassignment, " +
        // Which delivery this order rides on (orders sharing one show as a
        // single card). Raw internal key - listMine() swaps it for an opaque
        // per-response label and it never reaches the browser as-is.
        "CASE WHEN ld.Bulk_Delivery_ID IS NOT NULL THEN CONCAT('b', ld.Bulk_Delivery_ID) " +
        "WHEN ld.Parent_Light_Delivery_ID IS NOT NULL THEN CONCAT('l', ld.Parent_Light_Delivery_ID) " +
        "WHEN EXISTS (SELECT 1 FROM LIGHT_DELIVERIES c WHERE c.Parent_Light_Delivery_ID = ld.Light_Delivery_ID " +
        "AND c.Is_Deleted = FALSE) THEN CONCAT('l', ld.Light_Delivery_ID) END AS Delivery_Group_Raw, " +
        "pp.Status AS Payment_Status " +
        "FROM ORDERS o " +
        "LEFT JOIN DELIVERY_AREAS da ON da.Delivery_Area_ID = o.Delivery_Area_ID " +
        "LEFT JOIN LIGHT_DELIVERIES ld ON ld.Order_ID = o.Order_ID AND ld.Is_Deleted = FALSE " +
        "LEFT JOIN VEHICLE v ON v.Vehicle_ID = ld.Vehicle_ID " +
        "LEFT JOIN PRODUCT_PAYMENTS pp ON pp.Order_ID = o.Order_ID AND pp.Is_Deleted = FALSE ";

    // "if the body feet is above 500 then thats large for a single object
    // ... quantity is not an issue ... adjust for the small ones to be 3
    // weeks" - a single oversized item in the cart is enough to put the
    // whole order on the long SLA, since it's the one item holding up
    // the delivery either way.
    private static final BigDecimal LARGE_ITEM_THRESHOLD_BODY_FEET = BigDecimal.valueOf(500);
    private static final int LARGE_ITEM_SLA_MONTHS = 3;
    private static final int SMALL_ITEM_SLA_WEEKS = 3;

    // Daraja enforces a spike arrest of 30 requests/minute across the whole
    // app (verified against the sandbox - it answers HTTP 429
    // "policies.ratelimit.SpikeArrestViolation"). The confirmation page
    // polls each order every few seconds, so without a floor between
    // queries three shoppers checking out at once would exhaust the budget
    // and start starving each other's status checks.
    private static final long RECONCILE_MIN_INTERVAL_MS = 8_000;

    // CheckoutRequestID -> when it was last queried. Bounded by pruning
    // settled entries below; an STK prompt lives about a minute, so this
    // only ever holds the handful currently in flight.
    private final Map<String, Long> lastReconciledAt = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private final MpesaClient mpesaClient;
    private final String mediaBaseUrl;
    private final PhoneVerificationService phoneVerification;
    private final OrderTickets orderTickets;

    public OrderService(
        JdbcTemplate jdbcTemplate, MpesaClient mpesaClient,
        @Value("${app.media.base-url}") String mediaBaseUrl,
        PhoneVerificationService phoneVerification, OrderTickets orderTickets
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mpesaClient = mpesaClient;
        this.mediaBaseUrl = mediaBaseUrl;
        this.phoneVerification = phoneVerification;
        this.orderTickets = orderTickets;
    }

    // Carries a validated item's price from the stock-check pass to the
    // insert pass, so each product is only queried once per checkout.
    private record ResolvedItem(int productId, int qty, BigDecimal unitPrice) {}

    // "a user when paying for goods will select that section" - the
    // pickup/delivery area is now a required FK into DELIVERY_AREAS
    // (validated here, not a free-typed city), and every order gets its
    // own LIGHT_DELIVERIES row from the moment it's placed, so there's a
    // real, trackable delivery record for the confirmation page and the
    // help chat to report on - "i forgot to connect the shipping to the
    // actual delivery are table".
    @Transactional
    public CreateOrderResult createOrder(CreateOrderRequest request, int userId) {
        // "so that the phone number registered is verified" - while SMS codes
        // are set up, a customer confirms their number before the first order
        // (the website asks at checkout; staff accounts are made by admins) -
        // when it's on a network that gets codes (PhoneVerificationService).
        if (phoneVerification.isRequired()) {
            List<String> unconfirmed = jdbcTemplate.queryForList(
                "SELECT u.Phone_NO FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID " +
                "WHERE u.User_ID = ? AND r.Name = 'Customer' AND u.Phone_Verified_At IS NULL",
                String.class, userId
            );
            if (!unconfirmed.isEmpty() && phoneVerification.requiredFor(unconfirmed.get(0))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Please confirm your phone number first - we send a code to it by SMS.");
            }
        }
        if (request.deliveryAreaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a delivery/pickup area");
        }
        String city;
        try {
            city = jdbcTemplate.queryForObject(
                "SELECT City FROM DELIVERY_AREAS WHERE Delivery_Area_ID = ? AND Is_Deleted = FALSE AND Is_Active = TRUE",
                String.class, request.deliveryAreaId()
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That delivery area isn't available");
        }

        // The cart is checked before anything else is trusted about it: a
        // real cart has at least one line, every quantity is a whole number
        // of one or more (a zero or negative quantity would otherwise shrink
        // the total and even add phantom stock back), and the same product
        // listed twice is added together so its combined quantity is checked
        // against stock as one - two lines of 30 can't slip past a stock of
        // 50. The merged, validated lines are what the rest of this method
        // uses, never the raw request.
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty");
        }
        Map<Integer, Integer> mergedQty = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            if (item.qty() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item's quantity must be at least 1");
            }
            // addExact so two very large quantities can't wrap round to a
            // negative total that would then slip under the stock check.
            try {
                mergedQty.merge(item.productId(), item.qty(), Math::addExact);
            } catch (ArithmeticException overflow) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That quantity is too large");
            }
        }
        List<OrderItemRequest> items = mergedQty.entrySet().stream()
            .map(e -> new OrderItemRequest(e.getKey(), e.getValue()))
            .toList();

        // "if the stok is either e.g 5 and one orrders 6 the system should
        // have a way to flag this" - lock every item's row (FOR UPDATE, so
        // two concurrent checkouts on the last few units can't both pass
        // the check) and validate the whole cart against real stock before
        // writing anything, so a rejected order never creates a stub row.
        List<ResolvedItem> resolved = new ArrayList<>();
        List<String> shortfalls = new ArrayList<>();
        boolean isLargeOrder = false;
        for (OrderItemRequest item : items) {
            Map<String, Object> product;
            try {
                product = jdbcTemplate.queryForMap(
                    "SELECT Name, Type, Price, Stock, Unit, Body_Feet_Per_Unit FROM PRODUCTS " +
                    "WHERE Product_ID = ? AND Is_Deleted = FALSE AND Is_Retired = FALSE FOR UPDATE",
                    item.productId()
                );
            } catch (EmptyResultDataAccessException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product in your cart is no longer available");
            }
            BigDecimal stock = (BigDecimal) product.get("Stock");
            String unit = (String) product.get("Unit");
            String label = product.get("Name") != null ? (String) product.get("Name") : (String) product.get("Type");
            BigDecimal requested = BigDecimal.valueOf(item.qty());
            if (stock == null || requested.compareTo(stock) > 0) {
                String unitLabel = "board_feet".equals(unit) ? "board feet" : "in stock";
                shortfalls.add(String.format(
                    "%s - only %s %s left, %d requested",
                    label, stock == null ? "0" : stock.stripTrailingZeros().toPlainString(), unitLabel, item.qty()
                ));
            }
            BigDecimal bodyFeetPerUnit = (BigDecimal) product.get("Body_Feet_Per_Unit");
            if (bodyFeetPerUnit != null && bodyFeetPerUnit.compareTo(LARGE_ITEM_THRESHOLD_BODY_FEET) > 0) {
                isLargeOrder = true;
            }
            resolved.add(new ResolvedItem(item.productId(), item.qty(), (BigDecimal) product.get("Price")));
        }
        if (!shortfalls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough stock: " + String.join("; ", shortfalls));
        }
        LocalDate estimatedDeliveryDate = isLargeOrder
            ? LocalDate.now().plusMonths(LARGE_ITEM_SLA_MONTHS)
            : LocalDate.now().plusWeeks(SMALL_ITEM_SLA_WEEKS);

        jdbcTemplate.update(
            "INSERT INTO ORDERS (Total, Status, Created_At, Hotspot_City, Delivery_Area_ID, User_ID) " +
            "VALUES (0, 'PENDING', CURDATE(), ?, ?, ?)",
            city, request.deliveryAreaId(), userId
        );
        int orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        String ticketCode = orderTickets.assign(orderId);

        // Was previously summing item.qty() into this column instead of
        // qty*price - ORDERS.Total came out as a unit count, not the KES
        // amount every caller (the confirmation page, AiChatService's
        // account context) already treats it as. Fixing this here since
        // the STK push amount below needs the real total anyway.
        BigDecimal monetaryTotal = BigDecimal.ZERO;
        for (ResolvedItem item : resolved) {
            jdbcTemplate.update(
                "INSERT INTO ORDER_ITEMS (Qty, Unit_Price, Order_ID, Product_ID) VALUES (?, ?, ?, ?)",
                item.qty(), item.unitPrice(), orderId, item.productId()
            );
            jdbcTemplate.update(
                "INSERT INTO INVENTORY_MOVEMENT (Changes, Reason, Created_At, Product_ID) VALUES (?, 'ORDER', CURDATE(), ?)",
                "-" + item.qty(), item.productId()
            );
            jdbcTemplate.update(
                "UPDATE PRODUCTS SET Stock = Stock - ? WHERE Product_ID = ?",
                item.qty(), item.productId()
            );
            monetaryTotal = monetaryTotal.add(item.unitPrice().multiply(BigDecimal.valueOf(item.qty())));
        }

        jdbcTemplate.update(
            "UPDATE ORDERS SET Total = ? WHERE Order_ID = ?",
            monetaryTotal.setScale(0, RoundingMode.HALF_UP), orderId
        );

        jdbcTemplate.update(
            "INSERT INTO LIGHT_DELIVERIES (Status, Order_ID, Estimated_Delivery_Date) VALUES ('PENDING', ?, ?)",
            orderId, estimatedDeliveryDate
        );

        // "never allow an order to pass if the prompt fail all of this
        // orders are done soft cash no pay on delivery" - this used to
        // create the order regardless of what happened with the STK push
        // (a bad phone number, Safaricom being unreachable, M-Pesa not
        // configured all just logged a warning and left the order
        // standing, stock already deducted). Every order here needs a
        // successful push - there's no pay-on-delivery path - so a phone
        // number is required, and any failure now throws, which rolls
        // back this whole @Transactional method: the order, its items,
        // the stock deduction, all of it, leaving nothing half-done.
        if (request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your M-Pesa number to receive the payment prompt");
        }
        if (!mpesaClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "M-Pesa isn't configured on the server right now - try again shortly");
        }
        MpesaClient.StkPushResult result;
        try {
            result = mpesaClient.initiateStkPush(
                request.phoneNumber(), monetaryTotal, "ORD" + orderId, "Dateroem order #" + orderId
            );
        } catch (ResponseStatusException e) {
            // A plain input mistake (e.g. the phone number MpesaClient
            // rejected) - surface exactly as-is, nothing to log.
            throw e;
        } catch (Exception e) {
            // Logged with the exception, not just its message: a
            // swallowed one-line warning is what made this failure so
            // hard to pin down from the server's own output before.
            log.warn("STK push failed for Order_ID={}", orderId, e);
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Couldn't send the M-Pesa payment prompt (" + e.getMessage() + ") - check the number and try again"
            );
        }
        jdbcTemplate.update(
            "INSERT INTO PRODUCT_PAYMENTS (Provided, Status, Amount, Provider_Ref, Created_At, Description, Order_ID) " +
            "VALUES ('MPESA', 'PENDING', ?, ?, CURDATE(), 'STK push for website order', ?)",
            monetaryTotal, result.checkoutRequestId(), orderId
        );

        return new CreateOrderResult(orderId, true, null, ticketCode);
    }

    /**
     * "allow a user to drop the product if it has not yet been assigned a
     * trip which will allow one to get refunded the money paid for that
     * product but a cost is reduced from the initially sent by the admins
     * who can only do this and after it is dropped ... the stock ... will
     * go to 24" - admin-only (checked by the caller), blocked once the
     * order's own delivery has actually gone out on a trip (past that
     * point it isn't a clean cancellation any more), restores exactly the
     * quantity that was taken off each product, and records the
     * cancellation cost/refund figure on the order for a real audit trail
     * rather than just a note somewhere. Doesn't move any real money
     * itself - Send Money already exists for that once the admin has the
     * refund figure this returns.
     */
    @Transactional
    public Map<String, Object> dropOrder(int orderId, BigDecimal cancellationCost, int actorUserId) {
        Map<String, Object> order;
        try {
            order = jdbcTemplate.queryForMap(
                "SELECT Order_ID, Total, Status, User_ID FROM ORDERS WHERE Order_ID = ? AND Is_Deleted = FALSE",
                orderId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if ("CANCELLED".equals(order.get("Status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order was already dropped");
        }

        List<Integer> assignedTripIds = jdbcTemplate.queryForList(
            "SELECT Trip_ID FROM LIGHT_DELIVERIES WHERE Order_ID = ? AND Trip_ID IS NOT NULL AND Is_Deleted = FALSE",
            Integer.class, orderId
        );
        if (!assignedTripIds.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "This order is already on a trip's delivery run - it can't be dropped from here"
            );
        }

        BigDecimal total = BigDecimal.valueOf(((Number) order.get("Total")).longValue());
        BigDecimal cost = cancellationCost == null ? BigDecimal.ZERO : cancellationCost;
        if (cost.signum() < 0 || cost.compareTo(total) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation cost must be between 0 and the order total");
        }
        BigDecimal refund = total.subtract(cost);

        // Give the stock back, item by item - a positive movement mirrors
        // exactly how createOrder() took it away (Reason 'ORDER', negative).
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT Product_ID, Qty FROM ORDER_ITEMS WHERE Order_ID = ? AND Is_Deleted = FALSE", orderId
        );
        for (Map<String, Object> item : items) {
            int productId = ((Number) item.get("Product_ID")).intValue();
            Object qty = item.get("Qty");
            jdbcTemplate.update(
                "INSERT INTO INVENTORY_MOVEMENT (Changes, Reason, Created_At, Product_ID) VALUES (?, 'ORDER_CANCELLED', CURDATE(), ?)",
                "+" + qty, productId
            );
            jdbcTemplate.update("UPDATE PRODUCTS SET Stock = Stock + ? WHERE Product_ID = ?", qty, productId);
        }

        jdbcTemplate.update(
            "UPDATE ORDERS SET Status = 'CANCELLED', Cancellation_Cost = ?, Refunded_Amount = ?, " +
            "Cancelled_At = NOW(), Cancelled_By = ? WHERE Order_ID = ?",
            cost, refund, actorUserId, orderId
        );
        // Take it out of the pending pool too - a dropped order shouldn't
        // still show up as something waiting to be put on a delivery.
        jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE Order_ID = ? AND Trip_ID IS NULL AND Is_Deleted = FALSE",
            actorUserId, orderId
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "ORDERS", orderId,
            "Dropped - cancellation cost=" + cost + ", refund=" + refund + ", stock restored for " + items.size() + " item(s)",
            actorUserId
        );

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("cancellationCost", cost);
        result.put("refundedAmount", refund);
        result.put("customerUserId", order.get("User_ID"));
        return result;
    }

    // "where exactly do i go ... still cannot see it" - the Send Money
    // screen's target picker for ExpenseTarget.ORDER_REFUND: every dropped
    // order whose refund hasn't been paid out yet, same shape as every
    // other "pending holder" list (see FARMER_PAYMENTS/etc.) so it can
    // list-then-Pay the same way.
    public List<Map<String, Object>> listPendingRefunds() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT o.Order_ID, o.Ticket_Code, o.Refunded_Amount, o.Cancelled_At, o.User_ID, u.Name AS Customer_Name " +
            "FROM ORDERS o JOIN USERS u ON u.User_ID = o.User_ID " +
            "WHERE o.Status = 'CANCELLED' AND o.Refunded_Amount IS NOT NULL " +
            "AND o.Financial_Transaction_ID IS NULL AND o.Is_Deleted = FALSE " +
            "ORDER BY o.Cancelled_At DESC"
        ));
    }

    /**
     * "i need to see this" - the website's payments, newest first, for the
     * desktop's Website Payments list: the order, who paid, how much, the
     * outcome and the M-Pesa receipt (migration 035). A payment confirmed by
     * Safaricom's status query has no receipt number (that query returns
     * none), so receiptNote says so instead of leaving a blank.
     */
    public List<Map<String, Object>> listPayments(int limit) {
        List<Map<String, Object>> rows = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT pp.Product_Payment_ID, pp.Order_ID, o.Ticket_Code, pp.Amount, pp.Status, pp.Mpesa_Receipt, pp.Created_At, " +
            "pp.Description, o.Status AS Order_Status, u.Name AS Customer_Name, u.User_Name " +
            "FROM PRODUCT_PAYMENTS pp " +
            "JOIN ORDERS o ON o.Order_ID = pp.Order_ID " +
            "LEFT JOIN USERS u ON u.User_ID = o.User_ID " +
            "WHERE pp.Is_Deleted = FALSE AND pp.Order_ID IS NOT NULL " +
            "ORDER BY pp.Product_Payment_ID DESC LIMIT ?",
            Math.max(1, Math.min(limit, 1000))
        ));
        for (Map<String, Object> row : rows) {
            String description = (String) row.remove("description");
            if (row.get("mpesaReceipt") == null && "COMPLETED".equals(row.get("status"))) {
                row.put("receiptNote", "Paid - confirmed by Safaricom (no receipt number was sent)");
            } else if ("FAILED".equals(row.get("status"))) {
                row.put("receiptNote", description);
            }
        }
        return rows;
    }

    // PRODUCT_PAYMENTS.Description is varchar(250); Daraja's error text
    // plus our own prefix can run past that, and an over-long value would
    // abort the insert and lose the record entirely.
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    // Ownership-checked single-order lookup, backing the website's order
    // confirmation/status page.
    public Map<String, Object> get(int orderId, int requestingUserId) {
        Map<String, Object> order = loadOrder(orderId);
        if (!Objects.equals(((Number) order.get("User_ID")).intValue(), requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
        // Only after the ownership check, so a stranger guessing order ids
        // can't make us call out to Safaricom on their behalf.
        if (reconcilePendingPayment(order)) {
            order = loadOrder(orderId);
        }
        Map<String, Object> result = JsonKeys.camelCaseKeys(order);
        // "the area where one can see the products they paid for" - a
        // single-row queryForMap can't carry a one-to-many list, so the
        // line items are a second query, folded in under "items" once
        // the rest of the order is already camelCased.
        result.remove("deliveryGroupRaw");
        result.put("items", loadOrderItems(orderId));
        // "never allow them to see e.g order 39 that is the system ...
        // one doesn't have a clue of what index that order has since in
        // hacking that will be used to exploit the database" - the real
        // Order_ID is a global, sequential, cross-customer counter, so
        // showing it (even obfuscated) leaks how many orders exist in
        // total and roughly when this one landed relative to everyone
        // else's. This is that same customer's own 1st, 2nd, 3rd...
        // order instead - meaningful to them, meaningless to an attacker.
        result.put("customerOrderNumber", customerOrderNumber(requestingUserId, orderId));
        return result;
    }

    private int customerOrderNumber(int userId, int orderId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ORDERS WHERE User_ID = ? AND Order_ID <= ? AND Is_Deleted = FALSE",
            Integer.class, userId, orderId
        );
        return count == null ? 1 : count;
    }

    // "place them like cards the product ordered as well as a copy of the
    // image of the product ... since the above have not gotten it yet" -
    // the My Orders page's items never carried a product image before;
    // same relative-key-resolved-against-app.media.base-url treatment
    // ProductService's own listings already use (see MediaUrls).
    private List<Map<String, Object>> loadOrderItems(int orderId) {
        List<Map<String, Object>> items = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT oi.Qty, oi.Unit_Price, p.Product_ID, p.Name, p.Type, p.Unit AS Product_Unit, p.Image " +
            "FROM ORDER_ITEMS oi JOIN PRODUCTS p ON p.Product_ID = oi.Product_ID " +
            "WHERE oi.Order_ID = ? AND oi.Is_Deleted = FALSE",
            orderId
        ));
        for (Map<String, Object> item : items) {
            item.put("image", MediaUrls.resolve((String) item.get("image"), mediaBaseUrl));
        }
        return items;
    }

    private Map<String, Object> loadOrder(int orderId) {
        try {
            return jdbcTemplate.queryForMap(
                ORDER_WITH_DELIVERY_STATUS + "WHERE o.Order_ID = ? AND o.Is_Deleted = FALSE",
                orderId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    /**
     * Settles a still-pending payment by asking Safaricom outright, and
     * says whether anything changed.
     *
     * The callback is the intended path, but it can only arrive over a
     * public HTTPS address - in development that's an ngrok tunnel, and
     * whenever it isn't running (or its URL has rotated away from
     * app.mpesa.callback-url) nothing ever posts the result. The customer
     * pays, the order sits at PENDING forever, and the confirmation page
     * polls for two minutes and gives up still saying "check your phone" -
     * which is precisely what "the STK push doesn't work" looks like from
     * the shop side. The confirmation page already polls this order every
     * few seconds, so querying Daraja on those polls settles the payment
     * with no callback involved at all. A callback that does arrive still
     * wins - it carries the real M-Pesa receipt number, which this query
     * endpoint does not return.
     */
    private boolean reconcilePendingPayment(Map<String, Object> order) {
        if (!"PENDING".equals(order.get("Payment_Status")) || !mpesaClient.isConfigured()) {
            return false;
        }
        int orderId = ((Number) order.get("Order_ID")).intValue();
        String checkoutRequestId;
        try {
            checkoutRequestId = jdbcTemplate.queryForObject(
                "SELECT Provider_Ref FROM PRODUCT_PAYMENTS " +
                "WHERE Order_ID = ? AND Status = 'PENDING' AND Provider_Ref IS NOT NULL AND Is_Deleted = FALSE",
                String.class, orderId
            );
        } catch (EmptyResultDataAccessException e) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long previous = lastReconciledAt.get(checkoutRequestId);
        if (previous != null && now - previous < RECONCILE_MIN_INTERVAL_MS) {
            return false;
        }
        lastReconciledAt.put(checkoutRequestId, now);

        MpesaClient.StkStatusResult status = mpesaClient.queryStkStatus(checkoutRequestId);
        if (status.status() == MpesaClient.StkStatus.PENDING) {
            return false;
        }
        // Settled either way - no further polling will look at it.
        lastReconciledAt.remove(checkoutRequestId);
        if (status.status() == MpesaClient.StkStatus.SUCCESS) {
            // No receipt number to record - the query endpoint doesn't
            // return one, unlike the callback - so the description says
            // where the confirmation came from instead of inventing a code.
            int updated = jdbcTemplate.update(
                "UPDATE PRODUCT_PAYMENTS SET Status = 'COMPLETED', " +
                "Description = CONCAT(COALESCE(Description, ''), ' - confirmed via Daraja status query') " +
                "WHERE Provider_Ref = ? AND Status = 'PENDING' AND Is_Deleted = FALSE",
                checkoutRequestId
            );
            if (updated == 0) {
                // A callback landed in between and already settled it.
                return false;
            }
            jdbcTemplate.update("UPDATE ORDERS SET Status = 'PAID' WHERE Order_ID = ?", orderId);
            log.info("Order_ID={} payment confirmed by Daraja status query", orderId);
            return true;
        }
        String reason = "STK push failed (" + status.resultCode() + "): " + status.description();
        int updated = jdbcTemplate.update(
            "UPDATE PRODUCT_PAYMENTS SET Status = 'FAILED', Description = ? " +
            "WHERE Provider_Ref = ? AND Status = 'PENDING' AND Is_Deleted = FALSE",
            truncate(reason, 250),
            checkoutRequestId
        );
        if (updated == 0) {
            return false;
        }
        // A failure seen through the status query gets the same clean-up as
        // one seen through the callback: the reserved stock is given back
        // and the order is cancelled. Without this, a payment that failed
        // (cancelled prompt, timeout, wrong PIN) left the order stuck on
        // PENDING holding its stock for good - the gap noted in the
        // Go-Live checklist.
        cancelUnpaidOrder(orderId, reason);
        log.info("Order_ID={} payment failed per Daraja: {} {}", orderId, status.resultCode(), status.description());
        return true;
    }

    // Most-recent-first, used by the help chat to answer "where's my order"
    // and by the website's "Ordered Goods" page for whoever is currently
    // signed in.
    public List<Map<String, Object>> listMine(int userId, int limit) {
        List<Map<String, Object>> orders = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            ORDER_WITH_DELIVERY_STATUS + "WHERE o.User_ID = ? AND o.Is_Deleted = FALSE ORDER BY o.Order_ID DESC LIMIT ?",
            userId, limit
        ));
        // "provide the section ordered goods where one can see the
        // product" - each order needs its line items, not just the
        // total, so this mirrors get()'s single-order items lookup.
        // "the user in the website will see the grouped orders in one card" -
        // orders on the same delivery share a label ("g1", "g2", ...) that
        // means nothing outside this one response.
        Map<String, String> groupLabels = new java.util.LinkedHashMap<>();
        for (Map<String, Object> order : orders) {
            Object raw = order.remove("deliveryGroupRaw");
            if (raw != null) {
                order.put("deliveryGroup", groupLabels.computeIfAbsent(raw.toString(), k -> "g" + (groupLabels.size() + 1)));
            }
        }
        for (Map<String, Object> order : orders) {
            int orderId = ((Number) order.get("orderId")).intValue();
            order.put("items", loadOrderItems(orderId));
            // Same reasoning as get() - the customer's own Nth order, not
            // the platform-wide Order_ID, is what the website ever shows.
            order.put("customerOrderNumber", customerOrderNumber(userId, orderId));
        }
        return orders;
    }

    // Track my order: five wrong answers for one ticket lock it for half an
    // hour, and the page answers at most 60 look-ups a minute in all - so the
    // last three digits of a phone number (a thousand possibilities) can't
    // simply be tried one after another.
    private static final int TRACK_MAX_FAILURES = 5;
    private static final long TRACK_LOCK_MS = 30 * 60_000L;
    private static final int TRACK_MAX_PER_MINUTE = 60;
    private final Map<String, java.util.Deque<Long>> trackFailures = new ConcurrentHashMap<>();
    // Guarded by its own lock (only the overall per-minute count).
    private final java.util.Deque<Long> trackRecent = new java.util.ArrayDeque<>();

    /**
     * "Track my order" without signing in (23 October 2026): the ticket plus
     * the last 3 digits of the phone number on the customer's account. Only
     * website orders (made by a Customer account) can be tracked this way.
     * The answer says where the order is - never who placed it, their phone
     * or anything internal - and the driver and number plate only when staff
     * chose to share them for that delivery.
     */
    public Map<String, Object> track(String typedTicket, String phoneLastDigits) {
        String ticket = OrderTickets.normalise(typedTicket);
        String digits = phoneLastDigits == null ? "" : phoneLastDigits.replaceAll("\\D", "");
        if (ticket == null || digits.length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Enter your ticket (like DW-7K3Q9P) and the last 3 digits of your phone number.");
        }
        throttleTracking(ticket);

        List<Map<String, Object>> found = jdbcTemplate.queryForList(
            ORDER_WITH_DELIVERY_STATUS.replace("SELECT o.Order_ID,", "SELECT u.Phone_NO AS Owner_Phone, o.Order_ID,") +
            "JOIN USERS u ON u.User_ID = o.User_ID JOIN ROLE r ON r.Role_ID = u.Role_ID AND r.Name = 'Customer' " +
            "WHERE o.Ticket_Code = ? AND o.Is_Deleted = FALSE",
            ticket
        );
        String ownerDigits = found.isEmpty() || found.get(0).get("Owner_Phone") == null
            ? "" : found.get(0).get("Owner_Phone").toString().replaceAll("\\D", "");
        if (found.isEmpty() || ownerDigits.length() < 3 || !ownerDigits.endsWith(digits)) {
            trackFailures.computeIfAbsent(ticket, k -> new java.util.concurrent.ConcurrentLinkedDeque<>()).addLast(System.currentTimeMillis());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "We couldn't find an order with that ticket and phone number. Please check both and try again.");
        }
        trackFailures.remove(ticket);

        Map<String, Object> order = JsonKeys.camelCaseKeys(found.get(0));
        int orderId = ((Number) order.get("orderId")).intValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketCode", order.get("ticketCode"));
        result.put("stage", trackingStage(order));
        result.put("placedOn", order.get("createdAt"));
        result.put("estimatedDeliveryDate", order.get("estimatedDeliveryDate"));
        result.put("deliveredAt", order.get("deliveredAt"));
        result.put("areaName", order.get("areaName"));
        result.put("deliveryCity", order.get("deliveryCity"));
        result.put("total", order.get("total"));
        result.put("items", loadOrderItems(orderId));
        result.put("driverFirstName", order.get("driverFirstName"));
        result.put("vehicleIdentifier", order.get("vehicleIdentifier"));
        return result;
    }

    // One word for where the order is, for the Track my order page.
    private static String trackingStage(Map<String, Object> order) {
        String status = String.valueOf(order.get("status")).toUpperCase(java.util.Locale.ROOT);
        String payment = String.valueOf(order.get("paymentStatus")).toUpperCase(java.util.Locale.ROOT);
        if ("CANCELLED".equals(status) || "FAILED".equals(payment)) {
            return "CANCELLED";
        }
        if (order.get("deliveredAt") != null || "DELIVERED".equalsIgnoreCase(String.valueOf(order.get("deliveryStatus")))) {
            return "DELIVERED";
        }
        if (!"PAID".equals(status) && "PENDING".equals(payment)) {
            return "AWAITING_PAYMENT";
        }
        if (isTrue(order.get("needsReassignment"))) {
            return "RESCHEDULING";
        }
        return isTrue(order.get("isScheduled")) ? "SCHEDULED" : "PREPARING";
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() != 0);
    }

    private void throttleTracking(String ticket) {
        long now = System.currentTimeMillis();
        synchronized (trackRecent) {
            while (!trackRecent.isEmpty() && now - trackRecent.peekFirst() > 60_000L) {
                trackRecent.pollFirst();
            }
            if (trackRecent.size() >= TRACK_MAX_PER_MINUTE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Lots of people are checking orders right now - please try again in a minute.");
            }
            trackRecent.addLast(now);
        }
        java.util.Deque<Long> failures = trackFailures.get(ticket);
        if (failures != null) {
            failures.removeIf(at -> now - at > TRACK_LOCK_MS);
            if (failures.size() >= TRACK_MAX_FAILURES) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many tries for this ticket. Please wait half an hour, or contact us and we'll help.");
            }
        }
        // Old entries go once there are many, so the map can't grow without end.
        if (trackFailures.size() > 5_000) {
            trackFailures.entrySet().removeIf(e -> {
                Long last = e.getValue().peekLast();
                return last == null || now - last > TRACK_LOCK_MS;
            });
        }
    }

    // Real Daraja STK callback path for a website order's payment (as
    // opposed to TransactionService's staff-initiated FINANCIAL_TRANSACTION
    // pushes) - correlates by CheckoutRequestID, stored as Provider_Ref
    // when createOrder() initiated the push. Called from
    // TransactionService.handleMpesaCallback() when the checkout request id
    // doesn't match a FINANCIAL_TRANSACTION row, since Safaricom's callback
    // envelope carries no hint of which flow originated it. Returns false
    // when this checkout request id isn't one of ours either, so the caller
    // can log an "unknown" warning instead of silently doing nothing.
    @Transactional
    public boolean confirmPaymentByCheckoutRequestId(String checkoutRequestId, String receiptNumber) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "SELECT Product_Payment_ID, Order_ID, Status FROM PRODUCT_PAYMENTS " +
                "WHERE Provider_Ref = ? AND Is_Deleted = FALSE",
                checkoutRequestId
            );
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
        // A redelivered callback for an already-resolved payment - ack
        // without re-applying (Daraja retries anything that doesn't come
        // back clean, so this is the expected steady state, not an error).
        if (!"PENDING".equals(row.get("Status"))) {
            return true;
        }
        // "why does the database not show the reference ... in the desktop" -
        // the receipt also goes in its own column (migration 035), which the
        // desktop's Website Payments list and Orders page show. Only a real
        // receipt code is stored there, never the "confirmed by Safaricom"
        // note used when the callback carried none.
        String receipt = receiptNumber != null && receiptNumber.matches("[A-Z0-9]{8,12}") ? receiptNumber : null;
        jdbcTemplate.update(
            "UPDATE PRODUCT_PAYMENTS SET Status = 'COMPLETED', Mpesa_Receipt = ?, " +
            "Description = CONCAT(COALESCE(Description, ''), ' - receipt ', ?) " +
            "WHERE Product_Payment_ID = ?",
            receipt, receiptNumber, row.get("Product_Payment_ID")
        );
        jdbcTemplate.update("UPDATE ORDERS SET Status = 'PAID' WHERE Order_ID = ?", row.get("Order_ID"));
        return true;
    }

    // ResultCode != 0 half of the same callback - the customer cancelled or
    // the prompt timed out. "cannot create a new order till successful
    // message from safaricom happens" - marking only the PRODUCT_PAYMENTS
    // row FAILED used to leave the ORDERS row itself sitting there as a
    // real, stock-reserved order forever (Status stayed whatever
    // createOrder() set it to, never touched here) - the exact gap the
    // order #10 screenshot showed: a push that was sent but never paid
    // still looked like a placed order. This now cancels the order and
    // gives the stock back too, same restore logic as dropOrder(), so a
    // failed payment leaves no real order behind at all.
    @Transactional
    public boolean failPaymentByCheckoutRequestId(String checkoutRequestId, String reason) {
        Map<String, Object> payment;
        try {
            payment = jdbcTemplate.queryForMap(
                "SELECT Product_Payment_ID, Order_ID FROM PRODUCT_PAYMENTS WHERE Provider_Ref = ? AND Is_Deleted = FALSE",
                checkoutRequestId
            );
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
        int paymentId = ((Number) payment.get("Product_Payment_ID")).intValue();
        Integer orderId = (Integer) payment.get("Order_ID");

        jdbcTemplate.update(
            "UPDATE PRODUCT_PAYMENTS SET Status = 'FAILED', Description = CONCAT(COALESCE(Description, ''), ' - ', ?) " +
            "WHERE Product_Payment_ID = ?",
            reason, paymentId
        );

        if (orderId != null) {
            cancelUnpaidOrder(orderId, reason);
        }
        return true;
    }

    // Shared by failPaymentByCheckoutRequestId() above - gives back
    // exactly the stock/pending-delivery footprint createOrder() reserved,
    // mirroring dropOrder()'s own restore, minus the admin cancellation
    // cost/refund fields (nothing was ever actually paid here).
    private void cancelUnpaidOrder(int orderId, String reason) {
        Integer alreadyCancelled = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ORDERS WHERE Order_ID = ? AND Status IN ('CANCELLED', 'PAID')",
            Integer.class, orderId
        );
        if (alreadyCancelled != null && alreadyCancelled > 0) {
            return;
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT Product_ID, Qty FROM ORDER_ITEMS WHERE Order_ID = ? AND Is_Deleted = FALSE", orderId
        );
        for (Map<String, Object> item : items) {
            int productId = ((Number) item.get("Product_ID")).intValue();
            Object qty = item.get("Qty");
            jdbcTemplate.update(
                "INSERT INTO INVENTORY_MOVEMENT (Changes, Reason, Created_At, Product_ID) VALUES (?, 'ORDER_CANCELLED', CURDATE(), ?)",
                "+" + qty, productId
            );
            jdbcTemplate.update("UPDATE PRODUCTS SET Stock = Stock + ? WHERE Product_ID = ?", qty, productId);
        }
        jdbcTemplate.update("UPDATE ORDERS SET Status = 'CANCELLED' WHERE Order_ID = ?", orderId);
        jdbcTemplate.update(
            "UPDATE LIGHT_DELIVERIES SET Is_Deleted = TRUE, Deleted_At = NOW() " +
            "WHERE Order_ID = ? AND Trip_ID IS NULL AND Is_Deleted = FALSE",
            orderId
        );
        String payload = "Auto-cancelled - STK push not completed: " + reason;
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At) VALUES (?, ?, ?, CURDATE())",
            "ORDERS", orderId, payload.length() <= 250 ? payload : payload.substring(0, 250)
        );
    }
}
