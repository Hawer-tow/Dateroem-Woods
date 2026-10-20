package com.dateroem.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.DeviceProof;
import com.dateroem.backend.util.JsonKeys;

/**
 * "one can have multiple apps on phones ... and login in the same account
 * ... allow only one user per account per mobile phone ... since one can
 * use two phones and give one to a malicious person" - one approved phone
 * per account (USER_DEVICES, migration 030).
 *
 * A phone is recognised by a signing key the app creates in the phone's
 * secure hardware on first launch. At sign-in it signs a one-time challenge
 * from challenge(), which proves the request really comes from that install
 * - a copied password, a cloned app or a second phone all have a different
 * key. An account's first phone is approved automatically; every later one
 * waits (PENDING) until the SuperAdmin approves it in the desktop app, and only
 * one phone per account can be ACTIVE at a time (the database refuses a
 * second). Revoking a phone ends its session at once (MobileDeviceFilter).
 */
@Service
public class DeviceService {

    // Machine-readable prefixes the app looks for in the error message.
    public static final String PENDING = "DEVICE_PENDING";
    public static final String REVOKED = "DEVICE_REVOKED";

    private static final long CHALLENGE_TTL_MS = 2 * 60 * 1000L;
    private static final long LAST_SEEN_EVERY_MS = 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    // challenge -> expiry time; each one is accepted once.
    private final Map<String, Long> challenges = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSeenWritten = new ConcurrentHashMap<>();

    public DeviceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> challenge() {
        long now = System.currentTimeMillis();
        challenges.values().removeIf(expiry -> expiry < now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenges.put(challenge, now + CHALLENGE_TTL_MS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("challenge", challenge);
        result.put("expiresInSeconds", CHALLENGE_TTL_MS / 1000);
        return result;
    }

    /**
     * Checks the phone's proof and returns the id of the approved phone this
     * sign-in may use. Throws 403 with a DEVICE_PENDING or DEVICE_REVOKED
     * message when the phone isn't (or is no longer) approved for the account.
     * noRollbackFor: a new phone's PENDING row must be kept even though the
     * sign-in itself is refused - it is what the admin approves.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public int approvedDeviceForLogin(int userId, DeviceProof proof) {
        byte[] publicKeyBytes = verifyProof(proof);
        String fingerprint = sha256Hex(publicKeyBytes);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT Device_ID, Status FROM USER_DEVICES WHERE User_ID = ? AND Key_Fingerprint = ?",
            userId, fingerprint
        );
        if (!existing.isEmpty()) {
            int deviceId = ((Number) existing.get(0).get("Device_ID")).intValue();
            String status = (String) existing.get(0).get("Status");
            if ("ACTIVE".equals(status)) {
                jdbcTemplate.update(
                    "UPDATE USER_DEVICES SET Device_Model = ?, Os_Version = ?, App_Version = ?, Last_Seen_At = NOW() WHERE Device_ID = ?",
                    trim(proof.model(), 120), trim(proof.osVersion(), 40), trim(proof.appVersion(), 40), deviceId
                );
                return deviceId;
            }
            if ("REVOKED".equals(status)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    REVOKED + ": This phone has been blocked for this account. Ask the SuperAdmin if you need access.");
            }
            throw pending();
        }

        // An account's first phone is approved by itself, so nobody already
        // using the app is locked out; any phone after that needs an admin.
        Integer everApproved = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USER_DEVICES WHERE User_ID = ? AND Approved_At IS NOT NULL", Integer.class, userId
        );
        boolean firstPhone = everApproved == null || everApproved == 0;
        jdbcTemplate.update(
            "INSERT INTO USER_DEVICES (User_ID, Key_Fingerprint, Public_Key, Device_Model, Os_Version, App_Version, Status, Last_Seen_At) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NOW())",
            userId, fingerprint, proof.publicKey().trim(), trim(proof.model(), 120), trim(proof.osVersion(), 40), trim(proof.appVersion(), 40)
        );
        int deviceId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        if (firstPhone) {
            try {
                jdbcTemplate.update(
                    "UPDATE USER_DEVICES SET Status = 'ACTIVE', Approved_By = ?, Approved_At = NOW() WHERE Device_ID = ?",
                    userId, deviceId
                );
                audit(deviceId, "First phone approved automatically: " + describe(proof), userId);
                return deviceId;
            } catch (DuplicateKeyException raced) {
                // Another phone was approved for this account at the same moment.
            }
        }
        audit(deviceId, "New phone waiting for approval: " + describe(proof), userId);
        throw pending();
    }

    /** Whether this token's phone is still approved for this (enabled) account. */
    public boolean isActive(int userId, int deviceId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USER_DEVICES d JOIN USERS u ON u.User_ID = d.User_ID " +
            "WHERE d.Device_ID = ? AND d.User_ID = ? AND d.Status = 'ACTIVE' " +
            "AND u.Is_Deleted = FALSE AND (u.Status IS NULL OR u.Status = TRUE)",
            Integer.class, deviceId, userId
        );
        boolean active = count != null && count > 0;
        if (active) {
            long now = System.currentTimeMillis();
            Long last = lastSeenWritten.get(deviceId);
            if (last == null || now - last > LAST_SEEN_EVERY_MS) {
                lastSeenWritten.put(deviceId, now);
                jdbcTemplate.update("UPDATE USER_DEVICES SET Last_Seen_At = NOW() WHERE Device_ID = ?", deviceId);
            }
        }
        return active;
    }

    // ---------------------------------------------------------------- admin

    /** status: PENDING, ACTIVE, REVOKED, or null for every phone. */
    public List<Map<String, Object>> list(String status) {
        String filter = status == null || status.isBlank() ? "" : "WHERE d.Status = ? ";
        String sql =
            "SELECT d.Device_ID, d.User_ID, u.User_Name, u.Name, r.Name AS Role_Name, d.Device_Model, d.Os_Version, " +
            "d.App_Version, d.Status, d.Created_At, d.Approved_At, d.Approved_By, ab.Name AS Approved_By_Name, d.Revoked_At, " +
            "rb.Name AS Revoked_By_Name, d.Last_Seen_At " +
            "FROM USER_DEVICES d JOIN USERS u ON u.User_ID = d.User_ID JOIN ROLE r ON r.Role_ID = u.Role_ID " +
            "LEFT JOIN USERS ab ON ab.User_ID = d.Approved_By LEFT JOIN USERS rb ON rb.User_ID = d.Revoked_By " +
            filter +
            "ORDER BY FIELD(d.Status, 'PENDING', 'ACTIVE', 'REVOKED'), d.Device_ID DESC";
        return JsonKeys.camelCaseKeys(filter.isEmpty()
            ? jdbcTemplate.queryForList(sql)
            : jdbcTemplate.queryForList(sql, status.trim().toUpperCase()));
    }

    /**
     * Approves a waiting phone. If the account already has an approved
     * phone, replace=true revokes that one in the same step; without it the
     * request is refused so an admin never swaps phones by accident.
     */
    @Transactional
    public Map<String, Object> approve(int deviceId, boolean replace, int adminUserId) {
        Map<String, Object> device = load(deviceId);
        int userId = ((Number) device.get("User_ID")).intValue();
        if ("ACTIVE".equals(device.get("Status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This phone is already approved");
        }
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
            "SELECT Device_ID, Device_Model FROM USER_DEVICES WHERE User_ID = ? AND Status = 'ACTIVE'", userId
        );
        if (!current.isEmpty()) {
            if (!replace) {
                Object model = current.get(0).get("Device_Model");
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account already has an approved phone" + (model == null ? "" : " (" + model + ")") +
                    ". Approve and replace it, or revoke it first.");
            }
            int oldId = ((Number) current.get(0).get("Device_ID")).intValue();
            jdbcTemplate.update(
                "UPDATE USER_DEVICES SET Status = 'REVOKED', Revoked_By = ?, Revoked_At = NOW() WHERE Device_ID = ?",
                adminUserId, oldId
            );
            audit(oldId, "Phone replaced by phone #" + deviceId, adminUserId);
        }
        jdbcTemplate.update(
            "UPDATE USER_DEVICES SET Status = 'ACTIVE', Approved_By = ?, Approved_At = NOW(), Revoked_By = NULL, Revoked_At = NULL " +
            "WHERE Device_ID = ?",
            adminUserId, deviceId
        );
        audit(deviceId, "Phone approved", adminUserId);
        return JsonKeys.camelCaseKeys(load(deviceId));
    }

    /** Blocks a phone (approved or waiting); an approved one is signed out at once. */
    @Transactional
    public Map<String, Object> revoke(int deviceId, int adminUserId) {
        Map<String, Object> device = load(deviceId);
        if ("REVOKED".equals(device.get("Status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This phone is already blocked");
        }
        jdbcTemplate.update(
            "UPDATE USER_DEVICES SET Status = 'REVOKED', Revoked_By = ?, Revoked_At = NOW() WHERE Device_ID = ?",
            adminUserId, deviceId
        );
        audit(deviceId, "PENDING".equals(device.get("Status")) ? "Phone rejected" : "Phone revoked", adminUserId);
        return JsonKeys.camelCaseKeys(load(deviceId));
    }

    // ---------------------------------------------------------------- helpers

    private byte[] verifyProof(DeviceProof proof) {
        if (proof == null || blank(proof.publicKey()) || blank(proof.challenge()) || blank(proof.signature())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This version of the app can't sign in - please update it");
        }
        Long expiry = challenges.remove(proof.challenge());
        if (expiry == null || expiry < System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The sign-in took too long - please try again");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(proof.publicKey().trim());
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
            if (!(key instanceof ECPublicKey)) {
                throw new IllegalArgumentException("not an EC key");
            }
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(proof.challenge().getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(proof.signature().trim()))) {
                throw new IllegalArgumentException("signature mismatch");
            }
            return keyBytes;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This phone could not be verified - please try again");
        }
    }

    private Map<String, Object> load(int deviceId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT Device_ID, User_ID, Device_Model, Os_Version, App_Version, Status, Created_At, Approved_At, Revoked_At, Last_Seen_At " +
                "FROM USER_DEVICES WHERE Device_ID = ?",
                deviceId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phone not found");
        }
    }

    private static ResponseStatusException pending() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN,
            PENDING + ": This phone is waiting for approval. Ask the SuperAdmin to approve it in the desktop app (Users > Phones), then sign in again.");
    }

    private void audit(int deviceId, String payload, Integer actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('USER_DEVICES', ?, ?, CURDATE(), ?)",
            deviceId, payload.length() <= 250 ? payload : payload.substring(0, 250), actorUserId
        );
    }

    private static String describe(DeviceProof proof) {
        String model = blank(proof.model()) ? "unknown phone" : proof.model().trim();
        return blank(proof.osVersion()) ? model : model + ", Android " + proof.osVersion().trim();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
