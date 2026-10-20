package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.ProductDto;
import com.dateroem.backend.util.JsonKeys;
import com.dateroem.backend.util.MediaUrls;

@Service
public class ProductService {

    private static final String SELECT_COLUMNS =
        "Product_ID, Name, Ske, Type, Description, Description_Display, Price, Stock, Unit, Attributes, Body_Feet_Per_Unit, " +
        "Marketer_Note, Image, Trees_ID, Is_Deleted, Deleted_By, Deleted_At, Is_Retired, Retired_At, Retired_By";

    private final JdbcTemplate jdbcTemplate;
    private final String mediaBaseUrl;
    private final DescriptionFormatter descriptionFormatter;

    public ProductService(
        JdbcTemplate jdbcTemplate,
        @Value("${app.media.base-url}") String mediaBaseUrl,
        DescriptionFormatter descriptionFormatter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaBaseUrl = mediaBaseUrl;
        this.descriptionFormatter = descriptionFormatter;
    }

    // The tidied copy of the description is made in the background once the
    // save is committed - saving never waits for it.
    private void tidyDescriptionAfterCommit(int productId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    descriptionFormatter.formatLater(productId);
                }
            });
        } else {
            descriptionFormatter.formatLater(productId);
        }
    }

    private ProductDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductDto(
            rs.getInt("Product_ID"), rs.getString("Name"), rs.getString("Ske"),
            rs.getString("Type"), rs.getString("Description"), rs.getString("Description_Display"), rs.getBigDecimal("Price"),
            rs.getBigDecimal("Stock"), rs.getString("Unit"), rs.getString("Attributes"),
            rs.getBigDecimal("Body_Feet_Per_Unit"),
            rs.getString("Marketer_Note"),
            MediaUrls.resolve(rs.getString("Image"), mediaBaseUrl), (Integer) rs.getObject("Trees_ID"),
            (Boolean) rs.getObject("Is_Deleted"), (Integer) rs.getObject("Deleted_By"),
            rs.getObject("Deleted_At", java.time.LocalDateTime.class),
            (Boolean) rs.getObject("Is_Retired"), rs.getObject("Retired_At", java.time.LocalDateTime.class),
            (Integer) rs.getObject("Retired_By")
        );
    }

    // limit=null means "all" - the admin desktop's "specifying all, only
    // 50 etc" applies to every section's list, not just the raw table browser.
    // includeRetired is true only on the admin path (desktop/mobile): the
    // customer shop must never see a retired product, so the website server
    // calls this with it false.
    public List<ProductDto> list(Integer limit, boolean includeRetired) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM PRODUCTS WHERE Is_Deleted = FALSE" +
            (includeRetired ? "" : " AND Is_Retired = FALSE") + " ORDER BY Product_ID DESC";
        return limit == null
            ? jdbcTemplate.query(sql, this::mapRow)
            : jdbcTemplate.query(sql + " LIMIT ?", this::mapRow, limit);
    }

    // Admin lookup: a retired product is still returned (so it can be viewed,
    // edited or brought back). The customer path uses getForCustomer instead.
    public ProductDto get(int productId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM PRODUCTS WHERE Product_ID = ? AND Is_Deleted = FALSE",
                this::mapRow, productId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    // Customer lookup: a retired product is treated as not there, so its
    // shop page can't be opened (it "disappears from the shop") - a clean
    // 404, not a server error.
    public ProductDto getForCustomer(int productId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " + SELECT_COLUMNS + " FROM PRODUCTS WHERE Product_ID = ? AND Is_Deleted = FALSE AND Is_Retired = FALSE",
                this::mapRow, productId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This product is no longer available");
        }
    }

    // Admin-only "deleted products" view: bring back or permanently ignore
    // something soft-deleted, rather than it just vanishing.
    public List<ProductDto> listDeleted() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM PRODUCTS WHERE Is_Deleted = TRUE",
            this::mapRow
        );
    }

    // Netflix-style "most ordered" ranking, backed by fn_product_order_count
    // (migration 002) instead of computing popularity in application code.
    public List<Map<String, Object>> popular(int limit) {
        List<Map<String, Object>> rows = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT p.*, fn_product_order_count(p.Product_ID) AS OrderCount " +
            "FROM PRODUCTS p WHERE p.Is_Deleted = FALSE AND p.Is_Retired = FALSE " +
            "ORDER BY OrderCount DESC LIMIT ?",
            limit
        ));
        for (Map<String, Object> row : rows) {
            row.put("image", MediaUrls.resolve((String) row.get("image"), mediaBaseUrl));
        }
        return rows;
    }

    // @Transactional: PRODUCTS insert and its AUDIT_LOGS entry must succeed
    // or fail together - without this, a bad actorUserId rolled back the
    // audit log but left the product committed (caught via live testing).
    @Transactional
    public int create(ProductDto product, int actorUserId) {
        checkDescription(product);
        jdbcTemplate.update(
            "INSERT INTO PRODUCTS (Name, Ske, Type, Description, Price, Stock, Unit, Attributes, Body_Feet_Per_Unit, " +
            "Marketer_Note, Image, Trees_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            product.name(), product.sku(), product.type(), product.description(), product.price(),
            product.stock(), product.unit() == null ? "pieces" : product.unit(),
            product.attributes(), product.bodyFeetPerUnit(), product.marketerNote(), blankToNull(MediaUrls.toKey(product.image())),
            product.treesId()
        );
        int productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCTS", productId, "Created product: " + product.name(), actorUserId
        );
        tidyDescriptionAfterCommit(productId);
        return productId;
    }

    // Full-object update (same replace semantics as create, not a partial
    // PATCH) - covers editing any field including swapping the image key
    // after the fact, per "even after the creation you can still update
    // the picture". The one exception is the image: no image in the
    // request keeps the current one ("i edited a product all of a sudden
    // the image is lost" - the phone's edit form sent none when it couldn't
    // read the key back), and an empty one clears it on purpose.
    @Transactional
    public void update(int productId, ProductDto product, int actorUserId) {
        checkDescription(product);
        String image = MediaUrls.toKey(product.image());
        jdbcTemplate.update(
            "UPDATE PRODUCTS SET Name = ?, Ske = ?, Type = ?, Description = ?, Price = ?, " +
            "Stock = ?, Unit = ?, Attributes = ?, Body_Feet_Per_Unit = ?, Marketer_Note = ?, " +
            "Image = IF(? IS NULL, Image, NULLIF(?, '')), Trees_ID = ? " +
            "WHERE Product_ID = ? AND Is_Deleted = FALSE",
            product.name(), product.sku(), product.type(), product.description(), product.price(),
            product.stock(), product.unit() == null ? "pieces" : product.unit(),
            product.attributes(), product.bodyFeetPerUnit(), product.marketerNote(), image, image, product.treesId(), productId
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCTS", productId, "Updated product: " + product.name(), actorUserId
        );
        tidyDescriptionAfterCommit(productId);
    }

    // Soft delete only, per the standing rule for every table: mark it so
    // ordinary users stop seeing it, but keep the row (and who/when) for
    // the admin-only restore flow rather than losing it outright.
    @Transactional
    public void delete(int productId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE PRODUCTS SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE Product_ID = ? AND Is_Deleted = FALSE",
            actorUserId, productId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found or already deleted");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCTS", productId, "Deleted product", actorUserId
        );
    }

    @Transactional
    public void restore(int productId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE PRODUCTS SET Is_Deleted = FALSE, Deleted_By = NULL, Deleted_At = NULL " +
            "WHERE Product_ID = ? AND Is_Deleted = TRUE",
            productId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found or not deleted");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCTS", productId, "Restored product", actorUserId
        );
    }

    // "one can retire a product ... despite the stock being there or in an
    // event where the stock is depleted". Hides it from the shop and stops
    // new orders (OrderService refuses a retired product), but leaves its
    // stock and every existing order untouched. Not the same as delete: a
    // retired product stays in the admin lists with a "Retired" mark.
    @Transactional
    public void retire(int productId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE PRODUCTS SET Is_Retired = TRUE, Retired_By = ?, Retired_At = NOW() " +
            "WHERE Product_ID = ? AND Is_Deleted = FALSE AND Is_Retired = FALSE",
            actorUserId, productId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found or already retired");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCTS", productId, "Retired product", actorUserId
        );
    }

    // "it can be brought back incase it was being reviewd from a bad ratin
    // or comment" - back into the shop, stock and all, exactly as it was.
    @Transactional
    public void unretire(int productId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE PRODUCTS SET Is_Retired = FALSE, Retired_By = NULL, Retired_At = NULL " +
            "WHERE Product_ID = ? AND Is_Deleted = FALSE AND Is_Retired = TRUE",
            productId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found or not retired");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "PRODUCTS", productId, "Brought product back", actorUserId
        );
    }

    // "in an event where the stock is depleted the admins are notified" - the
    // products that have run out (stock at or below zero) but are still live
    // and not yet retired: the desktop/mobile "needs attention" list, where
    // an admin can retire one in a tap. Lowest stock (most depleted) first.
    public List<ProductDto> listDepleted() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM PRODUCTS " +
            "WHERE Is_Deleted = FALSE AND Is_Retired = FALSE AND (Stock IS NULL OR Stock <= 0) " +
            "ORDER BY Stock ASC, Product_ID DESC",
            this::mapRow
        );
    }

    // Description is TEXT since migration 027 ("the description ... can be
    // a lot"); this keeps a stray paste from reaching the database's own
    // limit as an unexplained error.
    private static final int DESCRIPTION_MAX = 10_000;

    private static void checkDescription(ProductDto product) {
        if (product.description() != null && product.description().length() > DESCRIPTION_MAX) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Description is too long - keep it under " + DESCRIPTION_MAX + " characters"
            );
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
