package com.dateroem.backend.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.security.DataCipher;
import com.dateroem.backend.util.JsonKeys;

/**
 * Worker details and documents (migration 040, 24 October 2026). "worker ID
 * number and documents with admin/SuperAdmin verification ... this system can
 * grow". The owner chose: the worker enters them on the phone; only the
 * SuperAdmin opens them - and so verifies them; Admins see only where each
 * worker stands; kept until the SuperAdmin deletes them.
 *
 * The fields and document types are rows (WORKER_FIELDS, WORKER_DOC_TYPES) the
 * SuperAdmin can add to. Sensitive values and every photo are encrypted
 * (DataCipher); every opening is written to AUDIT_LOGS.
 */
@Service
public class WorkerService {

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String VERIFIED = "VERIFIED";
    public static final String NEEDS_CHANGES = "NEEDS_CHANGES";
    private static final Set<String> KINDS = Set.of("text", "number", "date", "phone");
    private static final int MAX_VALUE = 120;
    private static final int MAX_PHOTO_BYTES = 3 * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final DataCipher cipher;

    public WorkerService(JdbcTemplate jdbcTemplate, DataCipher cipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
    }

    // --- definitions --------------------------------------------------------

    public List<Map<String, Object>> fields(boolean activeOnly) {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT Field_Key, Label, Kind, Hint, Is_Sensitive, Is_Required, Sort_Order, Is_Active FROM WORKER_FIELDS " +
            (activeOnly ? "WHERE Is_Active = TRUE " : "") + "ORDER BY Sort_Order, Field_Key"
        ));
    }

    public List<Map<String, Object>> docTypes(boolean activeOnly) {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT Type_Key, Label, Hint, Is_Required, Sort_Order, Is_Active FROM WORKER_DOC_TYPES " +
            (activeOnly ? "WHERE Is_Active = TRUE " : "") + "ORDER BY Sort_Order, Type_Key"
        ));
    }

    public Map<String, Object> definitions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields(false));
        result.put("docTypes", docTypes(false));
        return result;
    }

    /** SuperAdmin: a new field every worker can fill in from then on. */
    public Map<String, Object> addField(String label, String kind, String hint, boolean sensitive, boolean required, int actorUserId) {
        String l = cleanLabel(label);
        String k = kind == null ? "text" : kind.strip().toLowerCase(Locale.ROOT);
        if (!KINDS.contains(k)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose text, number, date or phone.");
        }
        String key = uniqueKey("WORKER_FIELDS", "Field_Key", l);
        jdbcTemplate.update(
            "INSERT INTO WORKER_FIELDS (Field_Key, Label, Kind, Hint, Is_Sensitive, Is_Required, Sort_Order) " +
            "VALUES (?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(f.Sort_Order), 0) + 10 FROM WORKER_FIELDS f))",
            key, l, k, blankToNull(hint), sensitive, required
        );
        audit("WORKER_FIELDS", 0, "Worker field added: " + l, actorUserId);
        return definitions();
    }

    public Map<String, Object> addDocType(String label, String hint, boolean required, int actorUserId) {
        String l = cleanLabel(label);
        String key = uniqueKey("WORKER_DOC_TYPES", "Type_Key", l);
        jdbcTemplate.update(
            "INSERT INTO WORKER_DOC_TYPES (Type_Key, Label, Hint, Is_Required, Sort_Order) " +
            "VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(d.Sort_Order), 0) + 10 FROM WORKER_DOC_TYPES d))",
            key, l, blankToNull(hint), required
        );
        audit("WORKER_DOC_TYPES", 0, "Worker document type added: " + l, actorUserId);
        return definitions();
    }

    /** SuperAdmin: stop (or start again) asking for a field or document; what was given is kept. */
    public Map<String, Object> setActive(String table, String keyColumn, String key, boolean active, int actorUserId) {
        int updated = jdbcTemplate.update("UPDATE " + table + " SET Is_Active = ? WHERE " + keyColumn + " = ?", active, key);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        audit(table, 0, (active ? "Asked for again: " : "No longer asked for: ") + key, actorUserId);
        return definitions();
    }

    // --- the worker's own (phone) -------------------------------------------

    public Map<String, Object> mine(int userId) {
        requireStaff(userId);
        Map<String, Object> result = new LinkedHashMap<>(record(userId));
        List<Map<String, Object>> fields = fields(true);
        Map<String, String> values = values(userId);
        for (Map<String, Object> field : fields) {
            field.put("value", values.get((String) field.get("fieldKey")));
        }
        result.put("fields", fields);
        result.put("docTypes", documentsFor(userId, true));
        return result;
    }

    @Transactional
    public Map<String, Object> saveMine(int userId, Map<String, String> given) {
        requireStaff(userId);
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        for (Map<String, Object> f : fields(true)) {
            fields.put((String) f.get("fieldKey"), f);
        }
        Map<String, String> before = values(userId);
        boolean changed = false;
        for (Map.Entry<String, String> entry : (given == null ? Map.<String, String>of() : given).entrySet()) {
            Map<String, Object> field = fields.get(entry.getKey());
            if (field == null) {
                continue;
            }
            String value = check(field, entry.getValue());
            if (java.util.Objects.equals(value, before.get(entry.getKey()))) {
                continue;
            }
            changed = true;
            if (value == null) {
                jdbcTemplate.update("DELETE FROM WORKER_FIELD_VALUES WHERE User_ID = ? AND Field_Key = ?", userId, entry.getKey());
            } else {
                String stored = truthy(field.get("isSensitive")) ? cipher.encryptText(value) : value;
                jdbcTemplate.update(
                    "INSERT INTO WORKER_FIELD_VALUES (User_ID, Field_Key, Value_Text, Updated_At) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE Value_Text = VALUES(Value_Text), Updated_At = VALUES(Updated_At)",
                    userId, entry.getKey(), stored, now()
                );
            }
        }
        if (changed) {
            afterChange(userId, "Worker details updated by the worker");
        }
        return mine(userId);
    }

    @Transactional
    public Map<String, Object> uploadMine(int userId, String typeKey, String base64Jpeg) {
        requireStaff(userId);
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM WORKER_DOC_TYPES WHERE Type_Key = ? AND Is_Active = TRUE", Integer.class, typeKey
        );
        if (active == null || active == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That document isn't asked for.");
        }
        byte[] photo;
        try {
            photo = Base64.getDecoder().decode(base64Jpeg == null ? "" : base64Jpeg);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a photo.");
        }
        if (photo.length < 4 || photo.length > MAX_PHOTO_BYTES || (photo[0] & 0xff) != 0xff || (photo[1] & 0xff) != 0xd8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a photo (at most 3 MB).");
        }
        // A new photo of the same document replaces the old one outright -
        // nothing kept that isn't needed.
        jdbcTemplate.update("DELETE FROM WORKER_DOCUMENTS WHERE User_ID = ? AND Type_Key = ?", userId, typeKey);
        jdbcTemplate.update(
            "INSERT INTO WORKER_DOCUMENTS (User_ID, Type_Key, Data, Uploaded_At) VALUES (?, ?, ?, ?)",
            userId, typeKey, cipher.encrypt(photo), now()
        );
        afterChange(userId, "Worker document uploaded by the worker: " + typeKey);
        return mine(userId);
    }

    // --- Admins (status only) and the SuperAdmin (everything) ----------------

    /** Every staff account and where its details stand - no numbers or photos. */
    public List<Map<String, Object>> list() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT u.User_ID, u.User_Name, u.Name, r.Name AS Role_Name, u.Status AS Active, " +
            "COALESCE(w.Status, 'NOT_STARTED') AS Worker_Status, w.Status_Note, w.Submitted_At, w.Verified_At, " +
            "(SELECT COUNT(*) FROM WORKER_FIELD_VALUES v WHERE v.User_ID = u.User_ID) AS Details_Given, " +
            "(SELECT COUNT(*) FROM WORKER_DOCUMENTS d WHERE d.User_ID = u.User_ID AND d.Is_Deleted = FALSE) AS Photos_Given " +
            "FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID LEFT JOIN WORKER_RECORDS w ON w.User_ID = u.User_ID " +
            "WHERE u.Is_Deleted = FALSE AND r.Name <> 'Customer' ORDER BY u.Name"
        ));
    }

    /** SuperAdmin: the worker's details, read in full - and logged. */
    public Map<String, Object> detail(int userId, int actorUserId) {
        Map<String, Object> result = new LinkedHashMap<>(record(userId));
        result.put("user", JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
            "SELECT u.User_ID, u.User_Name, u.Name, r.Name AS Role_Name FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID WHERE u.User_ID = ?",
            userId
        )));
        List<Map<String, Object>> fields = fields(false);
        Map<String, String> values = values(userId);
        for (Map<String, Object> field : fields) {
            field.put("value", values.get((String) field.get("fieldKey")));
        }
        result.put("fields", fields);
        result.put("docTypes", documentsFor(userId, false));
        audit("WORKER_RECORDS", userId, "Worker details opened by the SuperAdmin", actorUserId);
        return result;
    }

    public Map<String, Object> document(int userId, int documentId, int actorUserId) {
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "SELECT Type_Key, Data FROM WORKER_DOCUMENTS WHERE Document_ID = ? AND User_ID = ? AND Is_Deleted = FALSE",
                documentId, userId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        byte[] photo = cipher.decrypt((byte[]) row.get("Data"));
        audit("WORKER_DOCUMENTS", documentId, "Worker document opened by the SuperAdmin: " + row.get("Type_Key") + " of user " + userId, actorUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("typeKey", row.get("Type_Key"));
        result.put("contentType", "image/jpeg");
        result.put("data", Base64.getEncoder().encodeToString(photo));
        return result;
    }

    /** SuperAdmin: VERIFIED, or NEEDS_CHANGES with a note the worker sees. */
    public Map<String, Object> verify(int userId, String status, String note, int actorUserId) {
        String s = status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
        if (!VERIFIED.equals(s) && !NEEDS_CHANGES.equals(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose verified or needs changes.");
        }
        String n = blankToNull(note);
        if (NEEDS_CHANGES.equals(s) && n == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say what needs changing - the worker sees this note.");
        }
        ensureRecord(userId);
        jdbcTemplate.update(
            "UPDATE WORKER_RECORDS SET Status = ?, Status_Note = ?, Verified_By = ?, Verified_At = ?, Updated_At = ? WHERE User_ID = ?",
            s, n == null ? null : truncate(n), actorUserId, VERIFIED.equals(s) ? now() : null, now(), userId
        );
        audit("WORKER_RECORDS", userId, VERIFIED.equals(s) ? "Worker details verified" : "Worker details sent back: " + truncate(n), actorUserId);
        return detail(userId, actorUserId);
    }

    /** SuperAdmin: "keep until deleted by hand" - one photo. */
    @Transactional
    public void deleteDocument(int userId, int documentId, int actorUserId) {
        int deleted = jdbcTemplate.update("DELETE FROM WORKER_DOCUMENTS WHERE Document_ID = ? AND User_ID = ?", documentId, userId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        afterChange(userId, null);
        audit("WORKER_DOCUMENTS", documentId, "Worker document deleted by the SuperAdmin (user " + userId + ")", actorUserId);
    }

    /** SuperAdmin: everything kept about this worker, gone. */
    @Transactional
    public void deleteAll(int userId, int actorUserId) {
        jdbcTemplate.update("DELETE FROM WORKER_DOCUMENTS WHERE User_ID = ?", userId);
        jdbcTemplate.update("DELETE FROM WORKER_FIELD_VALUES WHERE User_ID = ?", userId);
        jdbcTemplate.update("DELETE FROM WORKER_RECORDS WHERE User_ID = ?", userId);
        audit("WORKER_RECORDS", userId, "All worker details and documents deleted by the SuperAdmin", actorUserId);
    }

    // --- helpers -------------------------------------------------------------

    private Map<String, Object> record(int userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT Status, Status_Note, Submitted_At, Verified_At FROM WORKER_RECORDS WHERE User_ID = ?", userId
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", rows.isEmpty() ? NOT_STARTED : rows.get(0).get("Status"));
        result.put("statusNote", rows.isEmpty() ? null : rows.get(0).get("Status_Note"));
        result.put("submittedAt", rows.isEmpty() ? null : rows.get(0).get("Submitted_At"));
        result.put("verifiedAt", rows.isEmpty() ? null : rows.get(0).get("Verified_At"));
        return result;
    }

    private Map<String, String> values(int userId) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT v.Field_Key, v.Value_Text, f.Is_Sensitive FROM WORKER_FIELD_VALUES v " +
            "JOIN WORKER_FIELDS f ON f.Field_Key = v.Field_Key WHERE v.User_ID = ?", userId
        )) {
            String stored = (String) row.get("Value_Text");
            result.put((String) row.get("Field_Key"), truthy(row.get("Is_Sensitive")) ? cipher.decryptText(stored) : stored);
        }
        return result;
    }

    // Document types with whether (and when) this worker sent one, and its id.
    private List<Map<String, Object>> documentsFor(int userId, boolean activeOnly) {
        List<Map<String, Object>> types = docTypes(activeOnly);
        Map<String, Map<String, Object>> sent = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
            "SELECT Document_ID, Type_Key, Uploaded_At FROM WORKER_DOCUMENTS WHERE User_ID = ? AND Is_Deleted = FALSE", userId
        )) {
            sent.put((String) row.get("Type_Key"), row);
        }
        for (Map<String, Object> type : types) {
            Map<String, Object> doc = sent.get((String) type.get("typeKey"));
            type.put("documentId", doc == null ? null : doc.get("Document_ID"));
            type.put("uploadedAt", doc == null ? null : doc.get("Uploaded_At"));
        }
        return types;
    }

    // A change by the worker: complete -> waiting for the SuperAdmin to check
    // (again, if it had been verified); otherwise still being filled in.
    private void afterChange(int userId, String auditText) {
        ensureRecord(userId);
        boolean complete = complete(userId);
        String current = (String) record(userId).get("status");
        String next = complete ? SUBMITTED : (NEEDS_CHANGES.equals(current) ? NEEDS_CHANGES : NOT_STARTED);
        jdbcTemplate.update(
            "UPDATE WORKER_RECORDS SET Status = ?, Submitted_At = CASE WHEN ? THEN ? ELSE Submitted_At END, " +
            "Verified_By = NULL, Verified_At = NULL, Updated_At = ? WHERE User_ID = ?",
            next, complete, now(), now(), userId
        );
        if (auditText != null) {
            audit("WORKER_RECORDS", userId, auditText, userId);
        }
    }

    private boolean complete(int userId) {
        Integer missingFields = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM WORKER_FIELDS f WHERE f.Is_Active = TRUE AND f.Is_Required = TRUE AND NOT EXISTS " +
            "(SELECT 1 FROM WORKER_FIELD_VALUES v WHERE v.User_ID = ? AND v.Field_Key = f.Field_Key)", Integer.class, userId
        );
        Integer missingDocs = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM WORKER_DOC_TYPES t WHERE t.Is_Active = TRUE AND t.Is_Required = TRUE AND NOT EXISTS " +
            "(SELECT 1 FROM WORKER_DOCUMENTS d WHERE d.User_ID = ? AND d.Type_Key = t.Type_Key AND d.Is_Deleted = FALSE)",
            Integer.class, userId
        );
        return (missingFields == null || missingFields == 0) && (missingDocs == null || missingDocs == 0);
    }

    private void ensureRecord(int userId) {
        jdbcTemplate.update(
            "INSERT IGNORE INTO WORKER_RECORDS (User_ID, Status, Updated_At) VALUES (?, ?, ?)", userId, NOT_STARTED, now()
        );
    }

    private String check(Map<String, Object> field, String raw) {
        String value = raw == null ? null : raw.strip();
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field.get("label") + " is too long.");
        }
        switch (String.valueOf(field.get("kind"))) {
            case "date" -> {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field.get("label") + ": choose a date.");
                }
            }
            case "number" -> {
                if (!value.matches("[0-9 ./-]{1,40}")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field.get("label") + " should be a number.");
                }
            }
            case "phone" -> {
                if (value.replaceAll("\\D", "").length() < 9) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field.get("label") + " isn't a full phone number.");
                }
            }
            default -> { }
        }
        return value;
    }

    private void requireStaff(int userId) {
        String role = jdbcTemplate.queryForObject(
            "SELECT r.Name FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID WHERE u.User_ID = ?", String.class, userId
        );
        if ("Customer".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Worker details are for staff accounts.");
        }
    }

    private String uniqueKey(String table, String column, String label) {
        String base = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        base = base.isEmpty() ? "item" : base.substring(0, Math.min(30, base.length()));
        String key = base;
        int n = 2;
        while (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, key) > 0) {
            key = base + "_" + n++;
        }
        return key;
    }

    private static String cleanLabel(String label) {
        String l = label == null ? "" : label.strip().replaceAll("\\s+", " ");
        if (l.length() < 2 || l.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give it a name (2 to 80 characters).");
        }
        return l;
    }

    private void audit(String table, int targetId, String payload, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            table, targetId, truncate(payload), actorUserId
        );
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 250 ? text : text.substring(0, 250);
    }

    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue() != 0);
    }
}
