package com.dateroem.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.util.MediaUrls;

/**
 * "for goods bought by a customer i want to allow them to give ratings to
 * the product so when one comes to buy the product one can see the star
 * reviews" - PRODUCT_REVIEWS (migration 026). A customer can rate a product
 * only once it has been delivered to them (the delivery worker marks it
 * delivered on the mobile app), one rating per product, with an optional
 * comment. Ratings are private unless the customer ticks "public" AND
 * accepts the public-review agreement; only then is their first name and
 * initial shown next to it.
 */
@Service
public class ReviewService {

    // "show ratings only after 2-3 ratings" - a product's stars, average and
    // reviews stay hidden until this many buyers have rated it, so a single
    // early score never defines a product.
    public static final int MIN_RATINGS_TO_SHOW = 3;

    // Must match REVIEW_CONSENT_VERSION in the website
    // (src/core/reviewConsent.js). Bump both whenever the agreement text
    // changes, so every public review records which wording was accepted.
    public static final String CONSENT_VERSION = "2026-09-26";

    private static final int COMMENT_MAX = 500;
    private static final int PUBLIC_REVIEWS_SHOWN = 50;

    // A purchase that can be rated: this customer's own order line, order not
    // cancelled or deleted, and the goods actually delivered - a light
    // delivery marked DELIVERED (light runs, and orders riding a bulk run),
    // or the order itself DELIVERED (heavy orders).
    private static final String DELIVERED_ITEMS =
        "SELECT oi.Product_ID, MAX(oi.Order_Items_ID) AS Order_Items_ID, " +
        "MAX(COALESCE(ld.Delivered_At, DATE(o.Created_At))) AS Delivered_On " +
        "FROM ORDER_ITEMS oi " +
        "JOIN ORDERS o ON o.Order_ID = oi.Order_ID AND o.Is_Deleted = FALSE AND o.Status <> 'CANCELLED' " +
        "LEFT JOIN LIGHT_DELIVERIES ld ON ld.Order_ID = o.Order_ID AND ld.Is_Deleted = FALSE " +
        "WHERE o.User_ID = ? AND oi.Is_Deleted = FALSE AND (ld.Status = 'DELIVERED' OR o.Status = 'DELIVERED') ";

    private final JdbcTemplate jdbcTemplate;
    private final String mediaBaseUrl;

    public ReviewService(JdbcTemplate jdbcTemplate, @Value("${app.media.base-url}") String mediaBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaBaseUrl = mediaBaseUrl;
    }

    public record SaveReviewRequest(Integer rating, String comment, Boolean makePublic, String consentVersion) {}

    // Everything a product page shows. Below the threshold only "not yet"
    // comes back - no count, no average, nothing about who rated.
    public Map<String, Object> productSummary(int productId) {
        Map<String, Object> totals = jdbcTemplate.queryForMap(
            "SELECT COUNT(*) AS Total, AVG(Rating) AS Average, " +
            "SUM(Rating = 5) AS S5, SUM(Rating = 4) AS S4, SUM(Rating = 3) AS S3, SUM(Rating = 2) AS S2, SUM(Rating = 1) AS S1, " +
            "SUM(Is_Public) AS Public_Count " +
            "FROM PRODUCT_REVIEWS WHERE Product_ID = ? AND Is_Hidden = FALSE",
            productId
        );
        int total = ((Number) totals.get("Total")).intValue();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("minimumToShow", MIN_RATINGS_TO_SHOW);
        if (total < MIN_RATINGS_TO_SHOW) {
            summary.put("shown", false);
            return summary;
        }
        summary.put("shown", true);
        summary.put("average", oneDecimal(totals.get("Average")));
        summary.put("count", total);
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            breakdown.put(String.valueOf(stars), number(totals.get("S" + stars)));
        }
        summary.put("breakdown", breakdown);
        int publicCount = number(totals.get("Public_Count"));
        summary.put("privateCount", total - publicCount);
        // Only what the customer agreed to show: the name they approved,
        // their stars, comment and the month - never ids or contact details.
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT Public_Name, Rating, Comment, COALESCE(Updated_At, Created_At) AS Rated_At FROM PRODUCT_REVIEWS " +
            "WHERE Product_ID = ? AND Is_Public = TRUE AND Is_Hidden = FALSE " +
            "ORDER BY COALESCE(Updated_At, Created_At) DESC LIMIT ?",
            productId, PUBLIC_REVIEWS_SHOWN
        )) {
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("name", row.get("Public_Name"));
            review.put("rating", number(row.get("Rating")));
            review.put("comment", row.get("Comment"));
            review.put("ratedMonth", String.valueOf(row.get("Rated_At")).substring(0, 7));
            reviews.add(review);
        }
        summary.put("reviews", reviews);
        return summary;
    }

    // Average and count per product for the product cards (and the website
    // assistant) - only products past the threshold appear at all.
    public Map<Integer, Map<String, Object>> summaries() {
        Map<Integer, Map<String, Object>> byProduct = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT Product_ID, COUNT(*) AS Total, AVG(Rating) AS Average FROM PRODUCT_REVIEWS " +
            "WHERE Is_Hidden = FALSE GROUP BY Product_ID HAVING COUNT(*) >= ?",
            MIN_RATINGS_TO_SHOW
        )) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("average", oneDecimal(row.get("Average")));
            summary.put("count", number(row.get("Total")));
            byProduct.put(number(row.get("Product_ID")), summary);
        }
        return byProduct;
    }

    // "once they rate a product then it can freely go out of their page but
    // they can still access it e.g to make ratings private" - delivered
    // products still waiting for a rating, and the ones already rated
    // (always editable), for the customer's Ordered Goods page.
    public Map<String, Object> mine(int userId) {
        List<Map<String, Object>> toRate = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT d.Product_ID, d.Delivered_On, p.Name, p.Type, p.Image FROM (" + DELIVERED_ITEMS + "GROUP BY oi.Product_ID) d " +
            "JOIN PRODUCTS p ON p.Product_ID = d.Product_ID AND p.Is_Deleted = FALSE " +
            "WHERE NOT EXISTS (SELECT 1 FROM PRODUCT_REVIEWS r WHERE r.Product_ID = d.Product_ID AND r.User_ID = ?) " +
            "ORDER BY d.Delivered_On DESC",
            userId, userId
        )) {
            toRate.add(productCard(row, row.get("Delivered_On")));
        }
        List<Map<String, Object>> rated = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT r.Product_ID, r.Rating, r.Comment, r.Is_Public, r.Public_Name, r.Consent_Version, r.Consent_At, r.Is_Hidden, " +
            "COALESCE(r.Updated_At, r.Created_At) AS Rated_At, p.Name, p.Type, p.Image " +
            "FROM PRODUCT_REVIEWS r JOIN PRODUCTS p ON p.Product_ID = r.Product_ID " +
            "WHERE r.User_ID = ? ORDER BY COALESCE(r.Updated_At, r.Created_At) DESC",
            userId
        )) {
            Map<String, Object> review = productCard(row, null);
            review.put("rating", number(row.get("Rating")));
            review.put("comment", row.get("Comment"));
            review.put("isPublic", truthy(row.get("Is_Public")));
            review.put("publicName", row.get("Public_Name"));
            review.put("consentVersion", row.get("Consent_Version"));
            review.put("consentAt", row.get("Consent_At") == null ? null : row.get("Consent_At").toString().substring(0, 10));
            review.put("isHidden", truthy(row.get("Is_Hidden")));
            review.put("ratedAt", String.valueOf(row.get("Rated_At")).substring(0, 10));
            rated.add(review);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toRate", toRate);
        result.put("rated", rated);
        result.put("consentVersion", CONSENT_VERSION);
        return result;
    }

    @Transactional
    public Map<String, Object> save(int userId, int productId, SaveReviewRequest request) {
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick between 1 and 5 stars");
        }
        String comment = request.comment() == null ? null : request.comment().strip();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > COMMENT_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the comment under " + COMMENT_MAX + " characters");
        }
        boolean makePublic = Boolean.TRUE.equals(request.makePublic());
        if (makePublic && !CONSENT_VERSION.equals(request.consentVersion())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Read and agree to the public review agreement to make your rating public"
            );
        }

        List<Map<String, Object>> purchase = jdbcTemplate.queryForList(
            DELIVERED_ITEMS + "AND oi.Product_ID = ? GROUP BY oi.Product_ID", userId, productId
        );
        if (purchase.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "You can rate a product once it has been delivered to you"
            );
        }
        int orderItemsId = number(purchase.get(0).get("Order_Items_ID"));

        Boolean wasPublic = jdbcTemplate.queryForList(
            "SELECT Is_Public FROM PRODUCT_REVIEWS WHERE Product_ID = ? AND User_ID = ?", Boolean.class, productId, userId
        ).stream().findFirst().orElse(null);

        String publicName = makePublic ? publicNameOf(userId) : null;
        // Is_Hidden is deliberately left alone on update - a review an admin
        // hid stays hidden however the customer edits it. Consent_At keeps
        // the original moment of agreement while the review stays public
        // under the same agreement; it is assigned first because MySQL
        // evaluates these left to right with already-updated values.
        jdbcTemplate.update(
            "INSERT INTO PRODUCT_REVIEWS (Product_ID, User_ID, Order_Items_ID, Rating, Comment, Is_Public, Public_Name, " +
            "Consent_Version, Consent_At) VALUES (?, ?, ?, ?, ?, ?, ?, ?, IF(?, NOW(), NULL)) " +
            "ON DUPLICATE KEY UPDATE " +
            "Consent_At = IF(VALUES(Is_Public) AND Is_Public AND Consent_Version <=> VALUES(Consent_Version), " +
            "Consent_At, VALUES(Consent_At)), " +
            "Order_Items_ID = VALUES(Order_Items_ID), Rating = VALUES(Rating), " +
            "Comment = VALUES(Comment), Is_Public = VALUES(Is_Public), Public_Name = VALUES(Public_Name), " +
            "Consent_Version = VALUES(Consent_Version)",
            productId, userId, orderItemsId, request.rating(), comment, makePublic, publicName,
            // Times come from MySQL's own clock (NOW()), same as Created_At -
            // a Java-side timestamp landed 3 hours off (UTC vs Kenya time).
            makePublic ? CONSENT_VERSION : null, makePublic
        );

        // The consent trail: every switch between public and private.
        boolean previouslyPublic = Boolean.TRUE.equals(wasPublic);
        if (makePublic != previouslyPublic) {
            audit(userId, productId, makePublic
                ? "Review made public as \"" + publicName + "\" (agreement " + CONSENT_VERSION + ")"
                : "Review made private (public consent withdrawn)");
        }
        return mine(userId);
    }

    @Transactional
    public Map<String, Object> delete(int userId, int productId) {
        Boolean wasPublic = jdbcTemplate.queryForList(
            "SELECT Is_Public FROM PRODUCT_REVIEWS WHERE Product_ID = ? AND User_ID = ?", Boolean.class, productId, userId
        ).stream().findFirst().orElse(null);
        if (wasPublic == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You haven't rated this product");
        }
        jdbcTemplate.update("DELETE FROM PRODUCT_REVIEWS WHERE Product_ID = ? AND User_ID = ?", productId, userId);
        audit(userId, productId, "Review removed by the customer" + (wasPublic ? " (was public)" : ""));
        return mine(userId);
    }

    // Admin moderation list - admins see who wrote each review (it's their
    // own customer data), unlike the public page.
    public List<Map<String, Object>> listForAdmin() {
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT r.Product_Review_ID, p.Name AS Product, u.Name AS Customer, r.Rating, r.Comment, r.Is_Public, " +
            "r.Public_Name, r.Is_Hidden, COALESCE(r.Updated_At, r.Created_At) AS Rated_At " +
            "FROM PRODUCT_REVIEWS r JOIN PRODUCTS p ON p.Product_ID = r.Product_ID " +
            "LEFT JOIN USERS u ON u.User_ID = r.User_ID ORDER BY COALESCE(r.Updated_At, r.Created_At) DESC"
        )) {
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("reviewId", number(row.get("Product_Review_ID")));
            review.put("product", row.get("Product"));
            review.put("customer", row.get("Customer"));
            review.put("rating", number(row.get("Rating")));
            review.put("comment", row.get("Comment"));
            review.put("visibility", truthy(row.get("Is_Public")) ? "Public as " + row.get("Public_Name") : "Private");
            review.put("hidden", truthy(row.get("Is_Hidden")));
            review.put("ratedAt", String.valueOf(row.get("Rated_At")).substring(0, 10));
            reviews.add(review);
        }
        return reviews;
    }

    @Transactional
    public void setHidden(int reviewId, boolean hidden, int adminUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE PRODUCT_REVIEWS SET Is_Hidden = ?, Hidden_By = ?, Hidden_At = IF(?, NOW(), NULL) WHERE Product_Review_ID = ?",
            hidden, hidden ? adminUserId : null, hidden, reviewId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCT_REVIEWS", reviewId, hidden ? "Review hidden by an admin" : "Review shown again by an admin", adminUserId
        );
    }

    // "only first name and initial is shown" - e.g. "Timothy K.", worked out
    // here from the account name so a customer can't publish someone else's.
    private String publicNameOf(int userId) {
        String name = jdbcTemplate.queryForList("SELECT Name FROM USERS WHERE User_ID = ?", String.class, userId)
            .stream().findFirst().orElse("");
        String[] parts = name == null ? new String[0] : name.strip().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "A verified buyer";
        }
        String first = capitalize(parts[0]);
        return parts.length > 1 ? first + " " + parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT) + "." : first;
    }

    private void audit(int userId, int productId, String payload) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCT_REVIEWS", productId, payload, userId
        );
    }

    private Map<String, Object> productCard(Map<String, Object> row, Object deliveredOn) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("productId", number(row.get("Product_ID")));
        card.put("name", row.get("Name") != null ? row.get("Name") : row.get("Type"));
        card.put("image", MediaUrls.resolve((String) row.get("Image"), mediaBaseUrl));
        if (deliveredOn != null) {
            card.put("deliveredOn", deliveredOn.toString());
        }
        return card;
    }

    // "timothy"/"TIMOTHY" -> "Timothy", but a name typed in mixed case
    // ("McDonald") is kept exactly as the customer wrote it.
    private static String capitalize(String word) {
        boolean singleCase = word.equals(word.toLowerCase(Locale.ROOT)) || word.equals(word.toUpperCase(Locale.ROOT));
        String rest = singleCase ? word.substring(1).toLowerCase(Locale.ROOT) : word.substring(1);
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + rest;
    }

    private static BigDecimal oneDecimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP);
    }

    private static int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value instanceof Number n && n.intValue() != 0;
    }
}
