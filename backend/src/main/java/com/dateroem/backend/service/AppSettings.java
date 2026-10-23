package com.dateroem.backend.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Settings that belong to the whole business (APP_SETTINGS, migration 039),
 * kept on the server so every phone and computer follows the same one.
 */
@Service
public class AppSettings {

    // "a timer like about 25 minutes of inactivity" - the desktop asks for
    // the password and the phone for the fingerprint after this many minutes
    // untouched. The SuperAdmin sets it.
    public static final String IDLE_LOCK_MINUTES = "idle_lock_minutes";
    public static final int DEFAULT_IDLE_LOCK_MINUTES = 25;
    public static final int MIN_IDLE_LOCK_MINUTES = 5;
    public static final int MAX_IDLE_LOCK_MINUTES = 120;

    private final JdbcTemplate jdbcTemplate;

    public AppSettings(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String get(String key) {
        List<String> values = jdbcTemplate.queryForList(
            "SELECT Setting_Value FROM APP_SETTINGS WHERE Setting_Key = ?", String.class, key
        );
        return values.isEmpty() ? null : values.get(0);
    }

    public void set(String key, String value, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO APP_SETTINGS (Setting_Key, Setting_Value, Updated_By, Updated_At) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE Setting_Value = VALUES(Setting_Value), Updated_By = VALUES(Updated_By), Updated_At = VALUES(Updated_At)",
            key, value, actorUserId, Timestamp.from(Instant.now())
        );
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('APP_SETTINGS', 0, ?, CURDATE(), ?)",
            truncate(key + " set to " + value), actorUserId
        );
    }

    public int idleLockMinutes() {
        try {
            int minutes = Integer.parseInt(String.valueOf(get(IDLE_LOCK_MINUTES)).trim());
            return Math.max(MIN_IDLE_LOCK_MINUTES, Math.min(MAX_IDLE_LOCK_MINUTES, minutes));
        } catch (NumberFormatException e) {
            return DEFAULT_IDLE_LOCK_MINUTES;
        }
    }

    public int setIdleLockMinutes(Integer minutes, int actorUserId) {
        if (minutes == null || minutes < MIN_IDLE_LOCK_MINUTES || minutes > MAX_IDLE_LOCK_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose between " + MIN_IDLE_LOCK_MINUTES + " and " + MAX_IDLE_LOCK_MINUTES + " minutes.");
        }
        set(IDLE_LOCK_MINUTES, String.valueOf(minutes), actorUserId);
        return minutes;
    }

    // The company card on the phone's Home ("only superadmin edits ... others
    // see the motto", 24 October 2026): one copy here for every phone, where
    // each phone used to keep its own. The defaults are the phone's old ones.
    public static final String COMPANY_NAME = "company_name";
    public static final String COMPANY_MOTTO = "company_motto";
    public static final String COMPANY_CODE = "company_code";

    public java.util.Map<String, Object> companyCard() {
        java.util.Map<String, Object> card = new java.util.LinkedHashMap<>();
        card.put("name", orDefault(get(COMPANY_NAME), "Dateroem Wood Sales"));
        card.put("motto", orDefault(get(COMPANY_MOTTO), "Quality timber, honest trade."));
        card.put("code", orDefault(get(COMPANY_CODE), "DWS-001"));
        return card;
    }

    public java.util.Map<String, Object> setCompanyCard(String name, String motto, String code, int actorUserId) {
        String n = name == null ? "" : name.strip();
        String m = motto == null ? "" : motto.strip();
        String c = code == null ? "" : code.strip();
        if (n.length() < 2 || n.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the company name (2 to 100 characters).");
        }
        if (m.length() > 200 || c.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The motto can have up to 200 characters and the code up to 30.");
        }
        set(COMPANY_NAME, n, actorUserId);
        set(COMPANY_MOTTO, m, actorUserId);
        set(COMPANY_CODE, c, actorUserId);
        return companyCard();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String text) {
        return text.length() <= 250 ? text : text.substring(0, 250);
    }
}
