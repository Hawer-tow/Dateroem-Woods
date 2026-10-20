package com.dateroem.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.ShowcaseRequest;
import com.dateroem.backend.util.JsonKeys;
import com.dateroem.backend.util.MediaUrls;

/**
 * The Bulk Orders page's showcase pictures (migration 034): "pictures that
 * can be used to show case what bulk orders look like so that customers can
 * see a template of what is meant by bulk orders ... like a slide". Never
 * products - never for sale, shown only in their own slideshow. Marketers,
 * Admins and the SuperAdmin change them (AdminGuard.requireShowcaseEditor);
 * anyone may read the current ones.
 */
@Service
public class ShowcaseService {

    static final String SECTION = "bulk_orders";
    // "rarely modified" - a slideshow, not a gallery: a dozen is plenty and
    // keeps the page quick on a phone.
    static final int MAX_PICTURES = 12;
    static final int MAX_CAPTION = 120;
    // Only a file the upload routes made in media/showcase/ - never any other
    // path or an outside address.
    private static final Pattern IMAGE_KEY = Pattern.compile("^showcase/[a-z0-9-]{1,80}\\.jpg$");

    private final JdbcTemplate jdbcTemplate;
    private final String mediaBaseUrl;

    public ShowcaseService(JdbcTemplate jdbcTemplate, @Value("${app.media.base-url}") String mediaBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaBaseUrl = mediaBaseUrl;
    }

    // In slideshow order.
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT Showcase_ID, Image, Caption, Sort_Order FROM SHOWCASE_IMAGES " +
            "WHERE Section = ? AND Is_Deleted = FALSE ORDER BY Sort_Order, Showcase_ID",
            SECTION
        ));
        for (Map<String, Object> row : rows) {
            row.put("image", MediaUrls.resolve((String) row.get("image"), mediaBaseUrl));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> create(ShowcaseRequest request, int actorUserId) {
        String key = request.image() == null ? null : MediaUrls.toKey(request.image().trim());
        if (key == null || !IMAGE_KEY.matcher(key).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the picture first");
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM SHOWCASE_IMAGES WHERE Section = ? AND Is_Deleted = FALSE", Integer.class, SECTION
        );
        if (count != null && count >= MAX_PICTURES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The slideshow already has " + MAX_PICTURES + " pictures - remove one before adding another");
        }
        Integer next = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(Sort_Order), 0) + 1 FROM SHOWCASE_IMAGES WHERE Section = ? AND Is_Deleted = FALSE",
            Integer.class, SECTION
        );
        jdbcTemplate.update(
            "INSERT INTO SHOWCASE_IMAGES (Section, Image, Caption, Sort_Order, Created_By) VALUES (?, ?, ?, ?, ?)",
            SECTION, key, cleanCaption(request.caption()), next, actorUserId
        );
        int id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        audit(id, "Added showcase picture", actorUserId);
        return one(id);
    }

    @Transactional
    public Map<String, Object> updateCaption(int showcaseId, ShowcaseRequest request, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE SHOWCASE_IMAGES SET Caption = ? WHERE Showcase_ID = ? AND Is_Deleted = FALSE",
            cleanCaption(request.caption()), showcaseId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Picture not found");
        }
        audit(showcaseId, "Edited showcase caption", actorUserId);
        return one(showcaseId);
    }

    // The new order must name every current picture exactly once, so a stale
    // screen can't silently drop or duplicate one.
    @Transactional
    public List<Map<String, Object>> reorder(List<Integer> showcaseIds, int actorUserId) {
        List<Integer> current = jdbcTemplate.queryForList(
            "SELECT Showcase_ID FROM SHOWCASE_IMAGES WHERE Section = ? AND Is_Deleted = FALSE", Integer.class, SECTION
        );
        if (showcaseIds == null || showcaseIds.size() != current.size()
            || !new HashSet<>(showcaseIds).equals(new HashSet<>(current))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The pictures changed meanwhile - refresh and try again");
        }
        for (int i = 0; i < showcaseIds.size(); i++) {
            jdbcTemplate.update("UPDATE SHOWCASE_IMAGES SET Sort_Order = ? WHERE Showcase_ID = ?", i + 1, showcaseIds.get(i));
        }
        audit(0, "Reordered showcase pictures", actorUserId);
        return list();
    }

    @Transactional
    public void delete(int showcaseId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE SHOWCASE_IMAGES SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() " +
            "WHERE Showcase_ID = ? AND Is_Deleted = FALSE",
            actorUserId, showcaseId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Picture not found");
        }
        audit(showcaseId, "Removed showcase picture", actorUserId);
    }

    private Map<String, Object> one(int showcaseId) {
        Map<String, Object> row = JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
            "SELECT Showcase_ID, Image, Caption, Sort_Order FROM SHOWCASE_IMAGES WHERE Showcase_ID = ?", showcaseId
        ));
        row.put("image", MediaUrls.resolve((String) row.get("image"), mediaBaseUrl));
        return row;
    }

    private static String cleanCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            return null;
        }
        String clean = caption.strip().replaceAll("\\s+", " ");
        if (clean.length() > MAX_CAPTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the caption to " + MAX_CAPTION + " characters");
        }
        return clean;
    }

    private void audit(int showcaseId, String payload, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "SHOWCASE_IMAGES", showcaseId, payload, actorUserId
        );
    }
}
