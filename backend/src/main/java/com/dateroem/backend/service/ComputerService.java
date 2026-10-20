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
import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.util.JsonKeys;

/**
 * "a standalone application that has higher privilege that can work even if
 * the backend is not in the same machine ... share it as an application to
 * other admins" - the desktop app's counterpart to DeviceService: it may be
 * used only on approved computers (USER_COMPUTERS, migration 031).
 *
 * A computer is recognised by a signing key the app creates in Windows' own
 * key store on its first sign-in. The key can never be exported, so copying
 * the app (or its files) to another computer does not copy it. At sign-in
 * the app signs a one-time challenge from challenge(), which proves the
 * request really comes from that computer - a stolen password alone gets
 * nowhere. One approved computer per account (the database refuses a
 * second); any other computer waits (PENDING) until the SuperAdmin approves
 * it from their approved computer ("the one who permits phones desktops is
 * the superadmin").
 *
 * Unlike phones, an Admin's first computer is NOT approved by itself; only
 * the SuperAdmin's own first computer is, since nobody else could approve
 * it. Revoking a computer, disabling the account or taking away its admin
 * role ends its session at once (DesktopClientFilter).
 */
@Service
public class ComputerService {

    // Machine-readable prefixes the desktop app looks for in the error message.
    public static final String PENDING = "COMPUTER_PENDING";
    public static final String REVOKED = "COMPUTER_REVOKED";

    private static final long CHALLENGE_TTL_MS = 2 * 60 * 1000L;
    private static final long LAST_SEEN_EVERY_MS = 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final RoleService roleService;
    // challenge -> expiry time; each one is accepted once.
    private final Map<String, Long> challenges = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSeenWritten = new ConcurrentHashMap<>();

    public ComputerService(JdbcTemplate jdbcTemplate, RoleService roleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleService = roleService;
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
     * Checks the computer's proof and returns the id of the approved
     * computer this sign-in may use. Throws 403 with a COMPUTER_PENDING or
     * COMPUTER_REVOKED message when the computer isn't (or is no longer)
     * approved for the account. noRollbackFor: a new computer's PENDING row
     * must be kept even though the sign-in itself is refused - it is what
     * the admin approves.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public int approvedComputerForLogin(int userId, DeviceProof proof) {
        byte[] publicKeyBytes = verifyProof(proof);
        String fingerprint = sha256Hex(publicKeyBytes);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT Computer_ID, Status FROM USER_COMPUTERS WHERE User_ID = ? AND Key_Fingerprint = ?",
            userId, fingerprint
        );
        if (!existing.isEmpty()) {
            int computerId = ((Number) existing.get(0).get("Computer_ID")).intValue();
            String status = (String) existing.get(0).get("Status");
            if ("ACTIVE".equals(status)) {
                jdbcTemplate.update(
                    "UPDATE USER_COMPUTERS SET Computer_Name = ?, Os_Version = ?, App_Version = ?, Last_Seen_At = NOW() WHERE Computer_ID = ?",
                    trim(proof.model(), 120), trim(proof.osVersion(), 40), trim(proof.appVersion(), 40), computerId
                );
                return computerId;
            }
            if ("REVOKED".equals(status)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    REVOKED + ": This computer has been blocked for this account. Ask the SuperAdmin if you need access.");
            }
            throw pending();
        }

        // Only the SuperAdmin's own first computer is approved by itself (no
        // one else could approve it); every other computer waits for them.
        boolean superAdminsFirst = false;
        if (RoleName.SuperAdmin.name().equals(roleService.roleNameOf(userId))) {
            Integer everApproved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USER_COMPUTERS WHERE User_ID = ? AND Approved_At IS NOT NULL", Integer.class, userId
            );
            superAdminsFirst = everApproved == null || everApproved == 0;
        }
        jdbcTemplate.update(
            "INSERT INTO USER_COMPUTERS (User_ID, Key_Fingerprint, Public_Key, Computer_Name, Os_Version, App_Version, Status, Last_Seen_At) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', NOW())",
            userId, fingerprint, proof.publicKey().trim(), trim(proof.model(), 120), trim(proof.osVersion(), 40), trim(proof.appVersion(), 40)
        );
        int computerId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        if (superAdminsFirst) {
            try {
                jdbcTemplate.update(
                    "UPDATE USER_COMPUTERS SET Status = 'ACTIVE', Approved_By = ?, Approved_At = NOW() WHERE Computer_ID = ?",
                    userId, computerId
                );
                audit(computerId, "SuperAdmin's first computer approved automatically: " + describe(proof), userId);
                return computerId;
            } catch (DuplicateKeyException raced) {
                // Another computer was approved for this account at the same moment.
            }
        }
        audit(computerId, "New computer waiting for approval: " + describe(proof), userId);
        throw pending();
    }

    /**
     * Whether this token's computer is still approved for this account, and
     * the account is still enabled and still Admin or SuperAdmin.
     */
    public boolean isActive(int userId, int computerId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USER_COMPUTERS c JOIN USERS u ON u.User_ID = c.User_ID JOIN ROLE r ON r.Role_ID = u.Role_ID " +
            "WHERE c.Computer_ID = ? AND c.User_ID = ? AND c.Status = 'ACTIVE' " +
            "AND u.Is_Deleted = FALSE AND (u.Status IS NULL OR u.Status = TRUE) AND r.Name IN (?, ?)",
            Integer.class, computerId, userId, RoleName.Admin.name(), RoleName.SuperAdmin.name()
        );
        boolean active = count != null && count > 0;
        if (active) {
            long now = System.currentTimeMillis();
            Long last = lastSeenWritten.get(computerId);
            if (last == null || now - last > LAST_SEEN_EVERY_MS) {
                lastSeenWritten.put(computerId, now);
                jdbcTemplate.update("UPDATE USER_COMPUTERS SET Last_Seen_At = NOW() WHERE Computer_ID = ?", computerId);
            }
        }
        return active;
    }

    // ---------------------------------------------------------------- admin

    /** status: PENDING, ACTIVE, REVOKED, or null for every computer. */
    public List<Map<String, Object>> list(String status) {
        String filter = status == null || status.isBlank() ? "" : "WHERE c.Status = ? ";
        String sql =
            "SELECT c.Computer_ID, c.User_ID, u.User_Name, u.Name, r.Name AS Role_Name, c.Computer_Name, c.Os_Version, " +
            "c.App_Version, c.Status, c.Created_At, c.Approved_At, c.Approved_By, ab.Name AS Approved_By_Name, c.Revoked_At, " +
            "rb.Name AS Revoked_By_Name, c.Last_Seen_At " +
            "FROM USER_COMPUTERS c JOIN USERS u ON u.User_ID = c.User_ID JOIN ROLE r ON r.Role_ID = u.Role_ID " +
            "LEFT JOIN USERS ab ON ab.User_ID = c.Approved_By LEFT JOIN USERS rb ON rb.User_ID = c.Revoked_By " +
            filter +
            "ORDER BY FIELD(c.Status, 'PENDING', 'ACTIVE', 'REVOKED'), c.Computer_ID DESC";
        return JsonKeys.camelCaseKeys(filter.isEmpty()
            ? jdbcTemplate.queryForList(sql)
            : jdbcTemplate.queryForList(sql, status.trim().toUpperCase()));
    }

    /**
     * Approves a waiting computer. If the account already has an approved
     * computer, replace=true revokes that one in the same step; without it
     * the request is refused so an admin never swaps computers by accident.
     */
    @Transactional
    public Map<String, Object> approve(int computerId, boolean replace, int adminUserId) {
        Map<String, Object> computer = load(computerId);
        int userId = ((Number) computer.get("User_ID")).intValue();
        if ("ACTIVE".equals(computer.get("Status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This computer is already approved");
        }
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
            "SELECT Computer_ID, Computer_Name FROM USER_COMPUTERS WHERE User_ID = ? AND Status = 'ACTIVE'", userId
        );
        if (!current.isEmpty()) {
            if (!replace) {
                Object name = current.get(0).get("Computer_Name");
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account already has an approved computer" + (name == null ? "" : " (" + name + ")") +
                    ". Approve and replace it, or block it first.");
            }
            int oldId = ((Number) current.get(0).get("Computer_ID")).intValue();
            jdbcTemplate.update(
                "UPDATE USER_COMPUTERS SET Status = 'REVOKED', Revoked_By = ?, Revoked_At = NOW() WHERE Computer_ID = ?",
                adminUserId, oldId
            );
            audit(oldId, "Computer replaced by computer #" + computerId, adminUserId);
        }
        jdbcTemplate.update(
            "UPDATE USER_COMPUTERS SET Status = 'ACTIVE', Approved_By = ?, Approved_At = NOW(), Revoked_By = NULL, Revoked_At = NULL " +
            "WHERE Computer_ID = ?",
            adminUserId, computerId
        );
        audit(computerId, "Computer approved", adminUserId);
        return JsonKeys.camelCaseKeys(load(computerId));
    }

    /** Blocks a computer (approved or waiting); an approved one is signed out at once. */
    @Transactional
    public Map<String, Object> revoke(int computerId, int adminUserId) {
        Map<String, Object> computer = load(computerId);
        if ("REVOKED".equals(computer.get("Status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This computer is already blocked");
        }
        jdbcTemplate.update(
            "UPDATE USER_COMPUTERS SET Status = 'REVOKED', Revoked_By = ?, Revoked_At = NOW() WHERE Computer_ID = ?",
            adminUserId, computerId
        );
        audit(computerId, "PENDING".equals(computer.get("Status")) ? "Computer rejected" : "Computer blocked", adminUserId);
        return JsonKeys.camelCaseKeys(load(computerId));
    }

    // ---------------------------------------------------------------- helpers

    private byte[] verifyProof(DeviceProof proof) {
        if (proof == null || blank(proof.publicKey()) || blank(proof.challenge()) || blank(proof.signature())) {
            throw new ResponseStatusException(HttpStatus.UPGRADE_REQUIRED, "This copy of the desktop app is too old - please install the new one.");
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This computer could not be verified - please try again");
        }
    }

    private Map<String, Object> load(int computerId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT Computer_ID, User_ID, Computer_Name, Os_Version, App_Version, Status, Created_At, Approved_At, Revoked_At, Last_Seen_At " +
                "FROM USER_COMPUTERS WHERE Computer_ID = ?",
                computerId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Computer not found");
        }
    }

    private static ResponseStatusException pending() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN,
            PENDING + ": This computer is waiting for approval. Ask the SuperAdmin to approve it in the desktop app (Users > Computers), then sign in again.");
    }

    private void audit(int computerId, String payload, Integer actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('USER_COMPUTERS', ?, ?, CURDATE(), ?)",
            computerId, payload.length() <= 250 ? payload : payload.substring(0, 250), actorUserId
        );
    }

    private static String describe(DeviceProof proof) {
        String name = blank(proof.model()) ? "unknown computer" : proof.model().trim();
        return blank(proof.osVersion()) ? name : name + ", " + proof.osVersion().trim();
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
