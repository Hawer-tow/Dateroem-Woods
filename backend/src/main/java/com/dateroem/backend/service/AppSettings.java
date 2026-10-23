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

    private static String truncate(String text) {
        return text.length() <= 250 ? text : text.substring(0, 250);
    }
}
