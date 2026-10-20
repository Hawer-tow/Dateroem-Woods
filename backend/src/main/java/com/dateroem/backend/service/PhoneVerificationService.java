package com.dateroem.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.util.KenyanNetworks;

/**
 * "one may give a phone number not theirs ... push a code to the phone
 * number they placed so that its verified" (17 October 2026, migration
 * 036). A 6-digit code goes by SMS (SmsSender) to the number typed; the
 * number counts as the customer's once they type that code back.
 *
 * - At sign-up there is no account yet: a correct code hands back a
 *   one-time proof (30 minutes) that register / Google sign-up must carry.
 * - A signed-in customer whose number isn't confirmed yet (accounts made
 *   before this, or a number an admin changed) confirms it at checkout; the
 *   account's number is then set to the confirmed one.
 *
 * Only hashes of codes and proofs are stored. Each code lasts 10 minutes
 * and allows 5 tries. Limits keep anyone from flooding a number with texts
 * (or running up the SMS bill): one code a minute, 3 per 15 minutes and 8 a
 * day per number, 8 a day per account, and 200 an hour in total. Rows are
 * deleted after 30 days.
 *
 * Codes go only to numbers on the networks in app.sms.networks (Airtel only
 * until the business has its own sender ID), and stop for 10 minutes when
 * the SMS service can't send (SmsSender.isAvailable); every other number is
 * taken as typed, exactly as before SMS codes.
 */
@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    static final int CODE_MINUTES = 10;
    static final int MAX_TRIES = 5;
    static final int PROOF_MINUTES = 30;
    static final int RESEND_SECONDS = 60;

    private final JdbcTemplate jdbcTemplate;
    private final SmsSender smsSender;
    private final MpesaClient mpesaClient;
    // app.sms.networks: the networks whose numbers get codes ("let it verify
    // for airtel only then we can switch to the safaricom if funds are
    // enough"); empty = every number. Others are taken as typed, as before.
    private final Set<String> networks;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(JdbcTemplate jdbcTemplate, SmsSender smsSender, MpesaClient mpesaClient,
                                    @Value("${app.sms.networks:all}") String networks) {
        this.jdbcTemplate = jdbcTemplate;
        this.smsSender = smsSender;
        this.mpesaClient = mpesaClient;
        Set<String> chosen = new LinkedHashSet<>();
        for (String name : (networks == null ? "" : networks).toLowerCase().split(",")) {
            if (!name.isBlank()) chosen.add(name.trim());
        }
        this.networks = chosen.contains("all") ? Set.of() : Set.copyOf(chosen);
    }

    /** False while SMS isn't set up: numbers are then taken as typed, as before. */
    public boolean isRequired() {
        return smsSender.isEnabled();
    }

    /**
     * Whether this number (any saved form) must be confirmed by a code right
     * now: SMS is set up and working, and the number is on a network that
     * gets codes.
     */
    public boolean requiredFor(String anyForm) {
        String phone254 = KenyanNetworks.to254(anyForm);
        return phone254 != null && isRequired() && smsSender.isAvailable()
            && (networks.isEmpty() || networks.contains(KenyanNetworks.of(phone254)));
    }

    /** The networks that get codes, for the sign-up screen ("Airtel"); null = all numbers. */
    public List<String> networkNames() {
        if (networks.isEmpty()) return null;
        return networks.stream().map(n -> Character.toUpperCase(n.charAt(0)) + n.substring(1)).toList();
    }

    /**
     * Sends a code to the number; userId is the signed-in account, or null at
     * sign-up. Answers {codeNeeded: false} without sending anything when this
     * number isn't asked for a code (another network, or SMS paused) - a
     * signed-in customer's account then takes that number as typed, as before.
     */
    @Transactional
    public Map<String, Object> sendCode(String rawPhone, Integer userId) {
        requireEnabled();
        String phone = mpesaClient.validateAndNormalizePhone(rawPhone == null ? "" : rawPhone.trim());
        if (!requiredFor(phone)) {
            return noCodeNeeded(phone, userId);
        }

        Map<String, Object> recent = jdbcTemplate.queryForMap(
            "SELECT " +
            "  COALESCE(SUM(Created_At > NOW() - INTERVAL ? SECOND), 0) AS lastMinute, " +
            "  COALESCE(SUM(Created_At > NOW() - INTERVAL 15 MINUTE), 0) AS lastQuarter, " +
            "  COUNT(*) AS lastDay " +
            "FROM PHONE_VERIFICATIONS WHERE Phone = ? AND Created_At > NOW() - INTERVAL 1 DAY",
            RESEND_SECONDS, phone
        );
        if (number(recent.get("lastMinute")) > 0) {
            throw tooMany("A code was just sent to this number. Please wait a minute before asking for another.");
        }
        if (number(recent.get("lastQuarter")) >= 3) {
            throw tooMany("Several codes were sent to this number already. Please wait 15 minutes and try again.");
        }
        if (number(recent.get("lastDay")) >= 8) {
            throw tooMany("Too many codes were sent to this number today. Please try again tomorrow, or contact us.");
        }
        if (userId != null) {
            Integer byUser = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PHONE_VERIFICATIONS WHERE User_ID = ? AND Created_At > NOW() - INTERVAL 1 DAY",
                Integer.class, userId
            );
            if (byUser != null && byUser >= 8) {
                throw tooMany("Too many codes were asked for today. Please try again tomorrow, or contact us.");
            }
        }
        Integer lastHour = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM PHONE_VERIFICATIONS WHERE Created_At > NOW() - INTERVAL 1 HOUR", Integer.class
        );
        if (lastHour != null && lastHour >= 200) {
            log.warn("Phone codes paused: {} sent in the last hour", lastHour);
            throw tooMany("We're sending a lot of codes right now. Please try again in a few minutes.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        jdbcTemplate.update(
            "INSERT INTO PHONE_VERIFICATIONS (Phone, User_ID, Code_Hash, Expires_At) VALUES (?, ?, ?, NOW() + INTERVAL ? MINUTE)",
            phone, userId, encoder.encode(code), CODE_MINUTES
        );
        // Sent after the row is written: a number that can't get texts is an
        // error that rolls the row back; an SMS service that couldn't send
        // (codes now paused) removes it and lets the customer carry on.
        if (smsSender.send(phone, "Dateroem Wood Sales: your code is " + code + ". It expires in " + CODE_MINUTES
            + " minutes. Don't share it. If you didn't ask for it, ignore this message.") == SmsSender.Outcome.UNAVAILABLE) {
            jdbcTemplate.update("DELETE FROM PHONE_VERIFICATIONS WHERE Verification_ID = LAST_INSERT_ID()");
            return noCodeNeeded(phone, userId);
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("codeNeeded", true);
        answer.put("sentTo", SmsSender.masked(phone));
        answer.put("expiresInSeconds", CODE_MINUTES * 60);
        answer.put("resendAfterSeconds", RESEND_SECONDS);
        return answer;
    }

    /**
     * Sign-up: checks the code and returns {phoneProof} - a one-time token
     * that register / Google sign-up use to show this number was confirmed.
     */
    @Transactional(noRollbackFor = WrongCode.class)
    public Map<String, Object> verifyForSignUp(String rawPhone, String code) {
        requireEnabled();
        String phone = mpesaClient.validateAndNormalizePhone(rawPhone == null ? "" : rawPhone.trim());
        long id = checkCode(phone, null, code);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbcTemplate.update("UPDATE PHONE_VERIFICATIONS SET Proof_Hash = ? WHERE Verification_ID = ?", sha256(proof), id);
        return Map.of("phoneProof", proof, "expiresInSeconds", PROOF_MINUTES * 60);
    }

    /**
     * A signed-in customer: checks the code, then makes the confirmed number
     * the account's number. Returns {phoneNo, phoneVerifiedAt}.
     */
    @Transactional(noRollbackFor = WrongCode.class)
    public Map<String, Object> verifyForAccount(int userId, String rawPhone, String code) {
        requireEnabled();
        String phone = mpesaClient.validateAndNormalizePhone(rawPhone == null ? "" : rawPhone.trim());
        checkCode(phone, userId, code);

        String saved = "0" + phone.substring(3);
        String before = jdbcTemplate.queryForObject("SELECT Phone_NO FROM USERS WHERE User_ID = ?", String.class, userId);
        jdbcTemplate.update("UPDATE USERS SET Phone_NO = ?, Phone_Verified_At = NOW() WHERE User_ID = ?", saved, userId);
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('USERS', ?, ?, CURDATE(), ?)",
            userId,
            "{\"action\":\"PHONE_VERIFIED\",\"phoneChanged\":" + !saved.equals(before) + "}",
            userId
        );
        LocalDateTime at = jdbcTemplate.queryForObject("SELECT Phone_Verified_At FROM USERS WHERE User_ID = ?", LocalDateTime.class, userId);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("phoneNo", saved);
        answer.put("phoneVerifiedAt", at);
        return answer;
    }

    /**
     * Sign-up: uses up the proof for this number. Returns false when
     * verification isn't required (SMS not set up); throws when it is and
     * the proof is missing, wrong, used or too old.
     */
    @Transactional
    public boolean useSignUpProof(String phone254, String proof) {
        boolean required = requiredFor(phone254);
        if (proof == null || proof.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please confirm your phone number with the code we send by SMS.");
            }
            return false;
        }
        // A code that was sent and typed still counts if codes paused since.
        int used = jdbcTemplate.update(
            "UPDATE PHONE_VERIFICATIONS SET Proof_Used_At = NOW() " +
            "WHERE Phone = ? AND Proof_Hash = ? AND Proof_Used_At IS NULL AND User_ID IS NULL " +
            "AND Verified_At > NOW() - INTERVAL ? MINUTE",
            phone254, sha256(proof.trim()), PROOF_MINUTES
        );
        if (used != 1 && required) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Your phone confirmation has expired or doesn't match this number. Please ask for a new code.");
        }
        return used == 1;
    }

    // The number isn't asked for a code: at sign-up nothing more is needed;
    // a signed-in customer's account takes the number as typed (unconfirmed),
    // the way every number was taken before SMS codes.
    private Map<String, Object> noCodeNeeded(String phone254, Integer userId) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("codeNeeded", false);
        if (userId != null) {
            String saved = "0" + phone254.substring(3);
            String before = jdbcTemplate.queryForObject("SELECT Phone_NO FROM USERS WHERE User_ID = ?", String.class, userId);
            if (!phone254.equals(KenyanNetworks.to254(before))) {
                jdbcTemplate.update("UPDATE USERS SET Phone_NO = ?, Phone_Verified_At = NULL WHERE User_ID = ?", saved, userId);
                jdbcTemplate.update(
                    "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('USERS', ?, ?, CURDATE(), ?)",
                    userId, "{\"action\":\"PHONE_CHANGED\",\"confirmed\":false}", userId
                );
            }
            answer.put("phoneNo", saved);
        }
        return answer;
    }

    /** Ties the used proof to the account that was just made (kept until the nightly clean-up). */
    public void linkProofToUser(String phone254, String proof, int userId) {
        jdbcTemplate.update(
            "UPDATE PHONE_VERIFICATIONS SET User_ID = ? WHERE Phone = ? AND Proof_Hash = ? AND User_ID IS NULL",
            userId, phone254, sha256(proof.trim())
        );
    }

    // Finds the newest live code for this number (and account, when signed
    // in), counts the try, and returns its id when the code matches.
    private long checkCode(String phone, Integer userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        if (!code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit code from the text message.");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT Verification_ID, Code_Hash, Attempts FROM PHONE_VERIFICATIONS " +
            "WHERE Phone = ? AND " + (userId == null ? "User_ID IS NULL" : "User_ID = ?") + " " +
            "AND Verified_At IS NULL AND Expires_At > NOW() ORDER BY Verification_ID DESC LIMIT 1 FOR UPDATE",
            userId == null ? new Object[] {phone} : new Object[] {phone, userId}
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This code has expired. Please ask for a new one.");
        }
        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("Verification_ID")).longValue();
        int attempts = ((Number) row.get("Attempts")).intValue();
        if (attempts >= MAX_TRIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Too many wrong tries. Please ask for a new code.");
        }
        if (!encoder.matches(code, (String) row.get("Code_Hash"))) {
            jdbcTemplate.update("UPDATE PHONE_VERIFICATIONS SET Attempts = Attempts + 1 WHERE Verification_ID = ?", id);
            int left = MAX_TRIES - attempts - 1;
            // WrongCode lets the transaction commit, so the counted try sticks.
            throw new WrongCode(left > 0
                ? "That code isn't right. " + left + (left == 1 ? " try" : " tries") + " left."
                : "That code isn't right, and that was the last try. Please ask for a new code.");
        }
        jdbcTemplate.update("UPDATE PHONE_VERIFICATIONS SET Verified_At = NOW() WHERE Verification_ID = ?", id);
        return id;
    }

    /** Old rows go every night at 03:20 - kept only for the limits above and sign-up checks. */
    @Scheduled(cron = "0 20 3 * * *")
    public void deleteOldRows() {
        int removed = jdbcTemplate.update("DELETE FROM PHONE_VERIFICATIONS WHERE Created_At < NOW() - INTERVAL 30 DAY");
        if (removed > 0) {
            log.info("Removed {} phone verification rows older than 30 days", removed);
        }
    }

    private void requireEnabled() {
        if (!isRequired()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Phone confirmation by SMS isn't set up yet.");
        }
    }

    private static ResponseStatusException tooMany(String message) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A wrong code: a 400 for the customer, but declared so @Transactional
     * still commits - the counted try must stick, or the 5-try limit could
     * be dodged.
     */
    public static class WrongCode extends ResponseStatusException {
        public WrongCode(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }
}
