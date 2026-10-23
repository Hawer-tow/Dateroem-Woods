package com.dateroem.backend.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * "superadmin in mobile cant seem to see the other details ... cards like
 * desktop" (24 October 2026): the business at a glance for the phone's Home -
 * the same figures and breakdowns as the desktop dashboard (users by role,
 * products in the shop / retired / out of stock, campaigns running and their
 * photos and videos, trips), plus the orders waiting for a delivery. Counted
 * here in one answer rather than fetching every list over a phone connection.
 * Admins and the SuperAdmin only.
 */
@Service
public class OverviewService {

    private final JdbcTemplate jdbcTemplate;

    public OverviewService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", users());
        result.put("products", products());
        result.put("campaigns", campaigns());
        result.put("trips", trips());
        result.put("orders", orders());
        return result;
    }

    private Map<String, Object> users() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT r.Name AS Role, COUNT(*) AS N, SUM(CASE WHEN u.Status = 1 THEN 0 ELSE 1 END) AS Disabled " +
            "FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID WHERE u.Is_Deleted = FALSE GROUP BY r.Name"
        );
        Map<String, Object> byRole = new LinkedHashMap<>();
        long total = 0;
        long customers = 0;
        long disabled = 0;
        for (Map<String, Object> row : rows) {
            long n = number(row.get("N"));
            total += n;
            disabled += number(row.get("Disabled"));
            if ("Customer".equals(row.get("Role"))) {
                customers = n;
            } else {
                byRole.put((String) row.get("Role"), n);
            }
        }
        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", total);
        users.put("customers", customers);
        users.put("staff", total - customers);
        users.put("staffByRole", byRole);
        users.put("disabled", disabled);
        return users;
    }

    private Map<String, Object> products() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND Is_Retired = FALSE), 0) AS In_Shop, " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND Is_Retired = TRUE), 0) AS Retired, " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND Is_Retired = FALSE AND COALESCE(Stock, 0) <= 0), 0) AS Out_Of_Stock, " +
            "  COALESCE(SUM(Is_Deleted = TRUE), 0) AS Deleted " +
            "FROM PRODUCTS"
        );
        Map<String, Object> products = new LinkedHashMap<>();
        products.put("inShop", number(row.get("In_Shop")));
        products.put("retired", number(row.get("Retired")));
        products.put("outOfStock", number(row.get("Out_Of_Stock")));
        products.put("deleted", number(row.get("Deleted")));
        return products;
    }

    // Running, upcoming and ended by their dates - the desktop's own rule.
    private Map<String, Object> campaigns() {
        String today = LocalDate.now().toString();
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT " +
            "  COALESCE(SUM(Is_Deleted = FALSE), 0) AS Total, " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND COALESCE(Start_At, '0000-01-01') <= ? AND COALESCE(End_At, '9999-12-31') >= ?), 0) AS Running, " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND Start_At > ?), 0) AS Upcoming, " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND UPPER(Media_Type) = 'IMAGE'), 0) AS Photos, " +
            "  COALESCE(SUM(Is_Deleted = FALSE AND UPPER(Media_Type) = 'VIDEO'), 0) AS Videos, " +
            "  COALESCE(SUM(Is_Deleted = TRUE), 0) AS Deleted " +
            "FROM CAMPAIGN",
            today, today, today
        );
        long total = number(row.get("Total"));
        long running = number(row.get("Running"));
        long upcoming = number(row.get("Upcoming"));
        Map<String, Object> campaigns = new LinkedHashMap<>();
        campaigns.put("total", total);
        campaigns.put("running", running);
        campaigns.put("upcoming", upcoming);
        campaigns.put("ended", Math.max(0, total - running - upcoming));
        campaigns.put("photos", number(row.get("Photos")));
        campaigns.put("videos", number(row.get("Videos")));
        campaigns.put("deleted", number(row.get("Deleted")));
        return campaigns;
    }

    private Map<String, Object> trips() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT COUNT(*) AS Total, COALESCE(SUM(End_Date IS NULL), 0) AS In_Progress, " +
            "COALESCE(SUM(NO_of_Deliveries), 0) AS Deliveries FROM TRIP WHERE Is_Deleted = FALSE"
        );
        long total = number(row.get("Total"));
        long inProgress = number(row.get("In_Progress"));
        Map<String, Object> trips = new LinkedHashMap<>();
        trips.put("total", total);
        trips.put("inProgress", inProgress);
        trips.put("finished", total - inProgress);
        trips.put("deliveries", number(row.get("Deliveries")));
        return trips;
    }

    // Orders not yet on a delivery - the same pool as Orders on the phone and
    // Insights > Orders on the desktop - and those that missed one.
    // How many are close to (DUE_SOON) or past (LATE) the date the customer
    // was promised - DeliveryDeadline.
    private Map<String, Object> orders() {
        List<Map<String, Object>> pool = jdbcTemplate.queryForList(
            "SELECT ld.Needs_Reassignment, ld.Estimated_Delivery_Date, o.Created_At " +
            "FROM LIGHT_DELIVERIES ld JOIN ORDERS o ON o.Order_ID = ld.Order_ID AND o.Is_Deleted = FALSE " +
            "WHERE ld.Trip_ID IS NULL AND ld.Status = 'PENDING' AND ld.Is_Deleted = FALSE"
        );
        LocalDate today = LocalDate.now();
        long reassign = 0;
        long dueSoon = 0;
        long late = 0;
        for (Map<String, Object> row : pool) {
            Object again = row.get("Needs_Reassignment");
            if (Boolean.TRUE.equals(again) || (again instanceof Number n && n.intValue() != 0)) {
                reassign++;
            }
            Object risk = DeliveryDeadline.of(row.get("Created_At"), row.get("Estimated_Delivery_Date"), today).get("deadlineRisk");
            if (DeliveryDeadline.LATE.equals(risk)) {
                late++;
            } else if (DeliveryDeadline.DUE_SOON.equals(risk)) {
                dueSoon++;
            }
        }
        Map<String, Object> orders = new LinkedHashMap<>();
        orders.put("waiting", (long) pool.size());
        orders.put("reassign", reassign);
        orders.put("dueSoon", dueSoon);
        orders.put("late", late);
        return orders;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }
}
