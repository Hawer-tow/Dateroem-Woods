package com.dateroem.backend.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CampaignDto;
import com.dateroem.backend.util.MediaUrls;

@Service
public class MarketingService {

    private static final String SELECT_COLUMNS =
        "Campaign_ID, Name, Channel, Start_At, End_At, Status, Media, Media_Type, Is_Deleted, Deleted_By, Deleted_At, " +
        "Is_Retired, Retired_At, Retired_By";

    private final JdbcTemplate jdbcTemplate;
    private final String mediaBaseUrl;

    public MarketingService(JdbcTemplate jdbcTemplate, @Value("${app.media.base-url}") String mediaBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaBaseUrl = mediaBaseUrl;
    }

    // Same resolution scheme as ProductService.resolveImageUrl - keeps the
    // "swap the base URL for a CDN in production" story identical for
    // every media type (photo, video) across the app.
    private String resolveMediaUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return mediaBaseUrl + key;
    }

    private CampaignDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CampaignDto(
            rs.getInt("Campaign_ID"), rs.getString("Name"), rs.getString("Channel"),
            rs.getObject("Start_At", LocalDate.class), rs.getObject("End_At", LocalDate.class),
            rs.getString("Status"), resolveMediaUrl(rs.getString("Media")), rs.getString("Media_Type"),
            (Boolean) rs.getObject("Is_Deleted"), (Integer) rs.getObject("Deleted_By"),
            rs.getObject("Deleted_At", java.time.LocalDateTime.class),
            rs.getBoolean("Is_Retired"), rs.getObject("Retired_At", java.time.LocalDateTime.class),
            (Integer) rs.getObject("Retired_By")
        );
    }

    // Clients only ever see the resolved address (the desktop the full
    // "http://127.0.0.1:8000/media/...", the phone "media/..."), so an edit
    // that sends it back must be stored as the bare key - the same fix
    // products got in MediaUrls.toKey ("i edited a product all of a sudden
    // the image is lost"). Campaigns lost their video the same way.
    private static String mediaKey(CampaignDto campaign) {
        String key = MediaUrls.toKey(campaign.media());
        return key == null || key.isBlank() ? null : key;
    }

    private void validateMedia(CampaignDto campaign) {
        boolean hasMedia = campaign.media() != null && !campaign.media().isBlank();
        if (hasMedia && !("VIDEO".equals(campaign.mediaType()) || "IMAGE".equals(campaign.mediaType()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaType must be VIDEO or IMAGE when media is set");
        }
    }

    // limit=null means "all" - same "specifying all, only 50 etc" story
    // as ProductService.list(). includeRetired is asked for only by the
    // apps' campaign lists (desktop/mobile); the website server leaves it
    // off, so a retired campaign never reaches a customer.
    public List<CampaignDto> list(Integer limit, boolean includeRetired) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM CAMPAIGN WHERE Is_Deleted = FALSE" +
            (includeRetired ? "" : " AND Is_Retired = FALSE") + " ORDER BY Campaign_ID DESC";
        return limit == null
            ? jdbcTemplate.query(sql, this::mapRow)
            : jdbcTemplate.query(sql + " LIMIT ?", this::mapRow, limit);
    }

    public CampaignDto get(int campaignId) {
        return jdbcTemplate.queryForObject(
            "SELECT " + SELECT_COLUMNS + " FROM CAMPAIGN WHERE Campaign_ID = ? AND Is_Deleted = FALSE",
            this::mapRow, campaignId
        );
    }

    public List<CampaignDto> listDeleted() {
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLUMNS + " FROM CAMPAIGN WHERE Is_Deleted = TRUE",
            this::mapRow
        );
    }

    // @Transactional: same reasoning as ProductService.create() - the
    // CAMPAIGN insert and its AUDIT_LOGS entry must commit or roll back together.
    @Transactional
    public int create(CampaignDto campaign, int actorUserId) {
        validateMedia(campaign);
        String media = mediaKey(campaign);
        jdbcTemplate.update(
            "INSERT INTO CAMPAIGN (Name, Channel, Start_At, End_At, Status, Media, Media_Type) VALUES (?, ?, ?, ?, ?, ?, ?)",
            campaign.name(), campaign.channel(), campaign.startAt(), campaign.endAt(), campaign.status(),
            media, media == null ? null : campaign.mediaType()
        );
        int campaignId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, "Created campaign: " + campaign.name(), actorUserId
        );
        return campaignId;
    }

    @Transactional
    public void update(int campaignId, CampaignDto campaign, int actorUserId) {
        validateMedia(campaign);
        String media = mediaKey(campaign);
        jdbcTemplate.update(
            "UPDATE CAMPAIGN SET Name = ?, Channel = ?, Start_At = ?, End_At = ?, Status = ?, Media = ?, Media_Type = ? " +
            "WHERE Campaign_ID = ? AND Is_Deleted = FALSE",
            campaign.name(), campaign.channel(), campaign.startAt(), campaign.endAt(), campaign.status(),
            media, media == null ? null : campaign.mediaType(), campaignId
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, "Updated campaign: " + campaign.name(), actorUserId
        );
    }

    @Transactional
    public void delete(int campaignId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE CAMPAIGN SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE Campaign_ID = ? AND Is_Deleted = FALSE",
            actorUserId, campaignId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found or already deleted");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, "Deleted campaign", actorUserId
        );
    }

    @Transactional
    public void restore(int campaignId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE CAMPAIGN SET Is_Deleted = FALSE, Deleted_By = NULL, Deleted_At = NULL " +
            "WHERE Campaign_ID = ? AND Is_Deleted = TRUE",
            campaignId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found or not deleted");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, "Restored campaign", actorUserId
        );
    }

    // "allow a campaign to be retired just like the products" - off the
    // website straight away, whatever its Status and dates; its media,
    // products and dates are kept, and it can be brought back.
    @Transactional
    public void retire(int campaignId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE CAMPAIGN SET Is_Retired = TRUE, Retired_By = ?, Retired_At = NOW() " +
            "WHERE Campaign_ID = ? AND Is_Deleted = FALSE AND Is_Retired = FALSE",
            actorUserId, campaignId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found or already retired");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, "Retired campaign", actorUserId
        );
    }

    @Transactional
    public void unretire(int campaignId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE CAMPAIGN SET Is_Retired = FALSE, Retired_By = NULL, Retired_At = NULL " +
            "WHERE Campaign_ID = ? AND Is_Deleted = FALSE AND Is_Retired = TRUE",
            campaignId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found or not retired");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, "Brought campaign back", actorUserId
        );
    }

    // requires migration 002 (CAMPAIGN_PRODUCTS) - links a campaign to the
    // products it promotes, so the frontend's marketing view page can show
    // "products and links to the products in the product view page".
    public void linkProduct(int campaignId, int productId) {
        jdbcTemplate.update(
            "INSERT INTO CAMPAIGN_PRODUCTS (Campaign_ID, Product_ID, Added_At) VALUES (?, ?, CURDATE())",
            campaignId, productId
        );
    }

    public List<Integer> linkedProductIds(int campaignId) {
        return jdbcTemplate.queryForList(
            "SELECT Product_ID FROM CAMPAIGN_PRODUCTS WHERE Campaign_ID = ?",
            Integer.class, campaignId
        );
    }

    /**
     * "Products in this campaign" in the phone and desktop apps: makes the
     * campaign promote exactly these products. The website's Featured page
     * shows a campaign only once it has at least one, and until now no app
     * could link (or unlink) any. Links to products that are in the bin
     * are left alone, so restoring such a product brings its link back.
     */
    @Transactional
    public List<Integer> setProducts(int campaignId, List<Integer> productIds, int actorUserId) {
        if (productIds == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productIds is required (an empty list removes every product)");
        }
        Integer campaigns = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM CAMPAIGN WHERE Campaign_ID = ? AND Is_Deleted = FALSE", Integer.class, campaignId
        );
        if (campaigns == null || campaigns == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found or deleted");
        }

        List<Integer> wanted = productIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Integer, String> labels = new HashMap<>();
        String placeholders = String.join(", ", Collections.nCopies(Math.max(wanted.size(), 1), "?"));
        if (!wanted.isEmpty()) {
            for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT Product_ID, COALESCE(NULLIF(TRIM(Name), ''), Type) AS Label FROM PRODUCTS " +
                "WHERE Is_Deleted = FALSE AND Product_ID IN (" + placeholders + ")",
                wanted.toArray()
            )) {
                labels.put(((Number) row.get("Product_ID")).intValue(), (String) row.get("Label"));
            }
            List<Integer> unknown = wanted.stream().filter(id -> !labels.containsKey(id)).toList();
            if (!unknown.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "These products don't exist or are in the bin: " + unknown);
            }
        }

        // Unlink the live products that are no longer chosen.
        List<Object> deleteArgs = new ArrayList<>();
        deleteArgs.add(campaignId);
        deleteArgs.addAll(wanted);
        jdbcTemplate.update(
            "DELETE cp FROM CAMPAIGN_PRODUCTS cp JOIN PRODUCTS p ON p.Product_ID = cp.Product_ID " +
            "WHERE cp.Campaign_ID = ? AND p.Is_Deleted = FALSE" +
            (wanted.isEmpty() ? "" : " AND cp.Product_ID NOT IN (" + placeholders + ")"),
            deleteArgs.toArray()
        );
        // Link the newly chosen ones; those already linked keep their date.
        List<Integer> linked = linkedProductIds(campaignId);
        for (Integer productId : wanted) {
            if (!linked.contains(productId)) {
                jdbcTemplate.update(
                    "INSERT INTO CAMPAIGN_PRODUCTS (Campaign_ID, Product_ID, Added_At) VALUES (?, ?, CURDATE())",
                    campaignId, productId
                );
            }
        }

        String payload = wanted.isEmpty()
            ? "Campaign products: none"
            : "Campaign products: " + String.join(", ", wanted.stream().map(labels::get).toList());
        if (payload.length() > 250) {
            payload = payload.substring(0, 247) + "...";
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) " +
            "VALUES (?, ?, ?, CURDATE(), ?)",
            "CAMPAIGN", campaignId, payload, actorUserId
        );
        return linkedProductIds(campaignId);
    }

    public void addAudienceMember(int campaignId, int userId) {
        jdbcTemplate.update(
            "INSERT INTO CAMPAIGN_AUDIENCE (Campaign_ID, User_ID, Assigned_At) VALUES (?, ?, CURDATE())",
            campaignId, userId
        );
    }
}
