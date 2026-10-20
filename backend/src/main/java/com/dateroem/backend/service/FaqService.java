package com.dateroem.backend.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.FaqRequest;
import com.dateroem.backend.util.JsonKeys;

/**
 * "adding faqs in the help area ... and add this faqs in the desktop for
 * editing adding new ones so that it constantly maintained" - the website's
 * Help page shows the published questions (FAQS, migration 032), grouped by
 * topic in Sort_Order; admins add, edit, hide, delete and restore them from
 * the desktop app. Every change is written to AUDIT_LOGS.
 */
@Service
public class FaqService {

    private static final String COLUMNS = "f.Faq_ID, f.Topic, f.Question, f.Answer, f.Link_Path, f.Sort_Order";
    // A page on the website itself ("/shipping", "/my-orders") - never an
    // outside address, so a question can't send customers off the site.
    private static final Pattern LINK_PATH = Pattern.compile("^/[a-z0-9/-]{0,119}$");
    private static final int MAX_ANSWER = 4000;

    private final JdbcTemplate jdbcTemplate;

    public FaqService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** What the website shows: published questions, in display order. */
    public List<Map<String, Object>> published() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT " + COLUMNS + " FROM FAQS f WHERE f.Is_Deleted = FALSE AND f.Is_Published = TRUE " +
            "ORDER BY f.Sort_Order, f.Faq_ID"
        ));
    }

    /** The desktop app's list: hidden questions too, or only the deleted ones. */
    public List<Map<String, Object>> manage(boolean deleted) {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT " + COLUMNS + ", f.Is_Published, f.Created_At, f.Updated_At, u.Name AS Updated_By_Name " +
            "FROM FAQS f LEFT JOIN USERS u ON u.User_ID = f.Updated_By WHERE f.Is_Deleted = ? " +
            "ORDER BY f.Sort_Order, f.Faq_ID",
            deleted
        ));
    }

    @Transactional
    public Map<String, Object> create(FaqRequest request, int actorUserId) {
        Clean faq = clean(request);
        jdbcTemplate.update(
            "INSERT INTO FAQS (Topic, Question, Answer, Link_Path, Sort_Order, Is_Published, Updated_At, Updated_By) " +
            "VALUES (?, ?, ?, ?, ?, ?, NOW(), ?)",
            faq.topic, faq.question, faq.answer, faq.linkPath, faq.sortOrder, faq.published, actorUserId
        );
        int faqId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        audit(faqId, "FAQ added: " + faq.question, actorUserId);
        return load(faqId);
    }

    @Transactional
    public Map<String, Object> update(int faqId, FaqRequest request, int actorUserId) {
        Clean faq = clean(request);
        int updated = jdbcTemplate.update(
            "UPDATE FAQS SET Topic = ?, Question = ?, Answer = ?, Link_Path = ?, Sort_Order = ?, Is_Published = ?, " +
            "Updated_At = NOW(), Updated_By = ? WHERE Faq_ID = ? AND Is_Deleted = FALSE",
            faq.topic, faq.question, faq.answer, faq.linkPath, faq.sortOrder, faq.published, actorUserId, faqId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }
        audit(faqId, (faq.published ? "FAQ edited: " : "FAQ edited (hidden): ") + faq.question, actorUserId);
        return load(faqId);
    }

    @Transactional
    public void delete(int faqId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE FAQS SET Is_Deleted = TRUE, Deleted_By = ?, Deleted_At = NOW() WHERE Faq_ID = ? AND Is_Deleted = FALSE",
            actorUserId, faqId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }
        audit(faqId, "FAQ deleted", actorUserId);
    }

    @Transactional
    public Map<String, Object> restore(int faqId, int actorUserId) {
        int updated = jdbcTemplate.update(
            "UPDATE FAQS SET Is_Deleted = FALSE, Deleted_By = NULL, Deleted_At = NULL WHERE Faq_ID = ? AND Is_Deleted = TRUE",
            faqId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found in the deleted list");
        }
        audit(faqId, "FAQ restored", actorUserId);
        return load(faqId);
    }

    // ---------------------------------------------------------------- helpers

    private record Clean(String topic, String question, String answer, String linkPath, int sortOrder, boolean published) {}

    private static Clean clean(FaqRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing was sent");
        }
        String topic = strip(request.topic());
        String question = strip(request.question());
        String answer = strip(request.answer());
        String linkPath = strip(request.linkPath());
        if (topic == null || topic.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a topic (up to 40 characters).");
        }
        if (question == null || question.length() > 250) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the question (up to 250 characters).");
        }
        if (answer == null || answer.length() > MAX_ANSWER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write the answer (up to " + MAX_ANSWER + " characters).");
        }
        if (linkPath != null && !LINK_PATH.matcher(linkPath).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The link must be a page on the website, like /shipping.");
        }
        int sortOrder = request.sortOrder() == null ? 100 : request.sortOrder();
        boolean published = request.published() == null || request.published();
        return new Clean(topic, question, answer, linkPath, sortOrder, published);
    }

    private static String strip(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.strip();
        return clean.isEmpty() ? null : clean;
    }

    private Map<String, Object> load(int faqId) {
        try {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
                "SELECT " + COLUMNS + ", f.Is_Published, f.Created_At, f.Updated_At FROM FAQS f WHERE f.Faq_ID = ?", faqId
            ));
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }
    }

    private void audit(int faqId, String payload, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('FAQS', ?, ?, CURDATE(), ?)",
            faqId, payload.length() <= 250 ? payload : payload.substring(0, 250), actorUserId
        );
    }
}
