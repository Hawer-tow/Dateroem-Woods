package com.dateroem.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.NoticeRequest;
import com.dateroem.backend.util.JsonKeys;

/**
 * "an information area ... e.g we might be having an update soon or new
 * features we can write them then we send them with a deadline for
 * elapsing" (26 October 2026) - the website's Updates (NOTICES, migration
 * 042). Admins write them in the desktop app; the website shows the
 * published ones until the end of their Show_Until day (Kenyan time), then
 * they leave by themselves. Every change is written to AUDIT_LOGS.
 */
@Service
public class NoticeService {

    private static final ZoneId KENYA = ZoneId.of("Africa/Nairobi");
    private static final String COLUMNS =
        "n.Notice_ID, n.Title, n.Message, DATE_FORMAT(n.Show_Until, '%Y-%m-%d') AS Show_Until";
    private static final int MAX_TITLE = 120;
    private static final int MAX_MESSAGE = 2000;
    // A notice is about something coming up soon, not a permanent page.
    private static final int MAX_DAYS_AHEAD = 366;

    private final JdbcTemplate jdbcTemplate;

    public NoticeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static LocalDate today() {
        return LocalDate.now(KENYA);
    }

    /** What the website shows: published notices still in date, newest first. */
    public List<Map<String, Object>> live() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT " + COLUMNS + ", DATE_FORMAT(n.Created_At, '%Y-%m-%d') AS Posted_On FROM NOTICES n " +
            "WHERE n.Is_Deleted = FALSE AND n.Is_Published = TRUE AND n.Show_Until >= ? " +
            "ORDER BY n.Notice_ID DESC LIMIT 20",
            today()
        ));
    }

    /** The desktop app's list: drafts and ended ones too, or only the deleted ones. */
    public List<Map<String, Object>> manage(boolean deleted) {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT " + COLUMNS + ", n.Is_Published, n.Show_Until >= ? AS Is_Current, n.Created_At, n.Updated_At, " +
            "u.Name AS Updated_By_Name FROM NOTICES n LEFT JOIN USERS u ON u.User_ID = COALESCE(n.Updated_By, n.Created_By) " +
            "WHERE n.Is_Deleted = ? ORDER BY n.Notice_ID DESC",
            today(), deleted
        ));
    }

    @Transactional
    public Map<String, Object> create(NoticeRequest request, int actorUserId) {
        Clean notice = clean(request, true);
        jdbcTemplate.update(
            "INSERT INTO NOTICES (Title, Message, Show_Until, Is_Published, Created_By) VALUES (?, ?, ?, ?, ?)",
            notice.title, notice.message, notice.showUntil, notice.published, actorUserId
        );
        int noticeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        audit(noticeId, "Notice added: " + notice.title, actorUserId);
        return load(noticeId);
    }

    @Transactional
    public Map<String, Object> update(int noticeId, NoticeRequest request, int actorUserId) {
        // An ended notice can be edited (say, to fix a word) without moving
        // its date; only a new date has to be today or later.
        LocalDate current = currentShowUntil(noticeId);
        Clean notice = clean(request, request == null || !current.equals(request.showUntil()));
        jdbcTemplate.update(
            "UPDATE NOTICES SET Title = ?, Message = ?, Show_Until = ?, Is_Published = ?, Updated_At = NOW(), Updated_By = ? " +
            "WHERE Notice_ID = ? AND Is_Deleted = FALSE",
            notice.title, notice.message, notice.showUntil, notice.published, actorUserId, noticeId
        );
        audit(noticeId, (notice.published ? "Notice edited: " : "Notice edited (draft): ") + notice.title, actorUserId);
        return load(noticeId);
    }

    @Transactional
    public void delete(int noticeId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE NOTICES SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Notice_ID = ? AND Is_Deleted = FALSE",
            actorUserId, noticeId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        }
        audit(noticeId, "Notice deleted", actorUserId);
    }

    @Transactional
    public Map<String, Object> restore(int noticeId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE NOTICES SET Is_Deleted = FALSE, Deleted_By = NULL, Deleted_At = NULL WHERE Notice_ID = ? AND Is_Deleted = TRUE",
            noticeId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found in the deleted list");
        }
        audit(noticeId, "Notice restored", actorUserId);
        return load(noticeId);
    }

    // ---------------------------------------------------------------- helpers

    private record Clean(String title, String message, LocalDate showUntil, boolean published) {}

    private static Clean clean(NoticeRequest request, boolean checkDate) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing was sent");
        }
        String title = strip(request.title());
        String message = strip(request.message());
        if (title == null || title.length() > MAX_TITLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a title (up to " + MAX_TITLE + " characters).");
        }
        if (message == null || message.length() > MAX_MESSAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the message (up to " + MAX_MESSAGE + " characters).");
        }
        LocalDate showUntil = request.showUntil();
        if (showUntil == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the last day it shows.");
        }
        if (checkDate && showUntil.isBefore(today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last day it shows can't be in the past.");
        }
        if (showUntil.isAfter(today().plusDays(MAX_DAYS_AHEAD))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The last day it shows must be within a year.");
        }
        boolean published = request.published() == null || request.published();
        return new Clean(title, message, showUntil, published);
    }

    private static String strip(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.strip();
        return clean.isEmpty() ? null : clean;
    }

    private LocalDate currentShowUntil(int noticeId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT Show_Until FROM NOTICES WHERE Notice_ID = ? AND Is_Deleted = FALSE", LocalDate.class, noticeId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        }
    }

    private Map<String, Object> load(int noticeId) {
        try {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
                "SELECT " + COLUMNS + ", n.Is_Published, n.Show_Until >= ? AS Is_Current, n.Created_At, n.Updated_At " +
                "FROM NOTICES n WHERE n.Notice_ID = ?",
                today(), noticeId
            ));
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        }
    }

    private void audit(int noticeId, String payload, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('NOTICES', ?, ?, CURDATE(), ?)",
            noticeId, payload.length() <= 250 ? payload : payload.substring(0, 250), actorUserId
        );
    }
}
