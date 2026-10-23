package com.dateroem.backend.service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.security.AccountSecurity;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.util.EmailAddresses;
import com.dateroem.backend.util.MediaUrls;

/**
 * "updating contacts/password ... a profile area" (migration 039): what a
 * signed-in person may change about their own account - name, email, photo
 * and password - and an admin's password reset. Nobody can ever see a
 * password: they are stored as one-way BCrypt hashes.
 *
 * The phone number is not here on purpose: a customer changes it with an SMS
 * code (PhoneVerificationService), and a staff member's number receives
 * payouts, so only an admin changes it (UserService).
 */
@Service
public class ProfileService {

    // Profile photos are saved by the website, phone and desktop servers into
    // media/profiles as u<user id>-<random>.jpg; only such a name, for the
    // account itself, is accepted here.
    private static final Pattern PHOTO_KEY = Pattern.compile("^profiles/u(\\d+)-[a-z0-9]{8,40}\\.jpg$");
    // A temporary password a person can read out or type: no 0/O, 1/l/I.
    private static final String TEMP_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int TEMP_LENGTH = 10;
    // Wrong current passwords (changing it, or unlocking the desktop app):
    // five in 15 minutes and the account must wait.
    private static final int MAX_WRONG = 5;
    private static final long WRONG_WINDOW_MS = 15 * 60_000L;

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final AccountSecurity accountSecurity;
    private final RoleService roleService;
    private final String mediaBaseUrl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, Deque<Long>> wrongPasswords = new ConcurrentHashMap<>();

    public ProfileService(JdbcTemplate jdbcTemplate, JwtService jwtService, AccountSecurity accountSecurity,
                          RoleService roleService, @Value("${app.media.base-url}") String mediaBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.accountSecurity = accountSecurity;
        this.roleService = roleService;
        this.mediaBaseUrl = mediaBaseUrl;
    }

    public String photoUrl(Object photoKey) {
        return photoKey instanceof String key && !key.isBlank() ? MediaUrls.resolve(key, mediaBaseUrl) : null;
    }

    /** Name and email, from the person's own profile. */
    public Map<String, Object> updateProfile(int userId, String name, String email, String currentPassword) {
        Map<String, Object> row = account(userId);
        String newName = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (newName.length() < 2 || newName.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name (2 to 100 characters).");
        }
        String newEmail = EmailAddresses.normalize(email);
        String oldEmail = (String) row.get("Email");
        boolean emailChanged = !(newEmail == null ? oldEmail == null || oldEmail.isBlank() : newEmail.equalsIgnoreCase(oldEmail));
        if (emailChanged) {
            if (newEmail != null) {
                String problem = EmailAddresses.problem(newEmail);
                if (problem != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
                }
                Integer taken = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM USERS WHERE LOWER(Email) = LOWER(?) AND User_ID <> ? AND Is_Deleted = FALSE",
                    Integer.class, newEmail, userId
                );
                if (taken != null && taken > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Another account already uses this email.");
                }
            }
            // The email can be used to sign in, so changing it asks for the
            // password (an account that signs in with Google has none).
            String hash = (String) row.get("Password");
            if (hash != null) {
                checkPassword(userId, hash, currentPassword, "Enter your current password to change your email.");
            }
        }
        jdbcTemplate.update("UPDATE USERS SET Name = ?, Email = ? WHERE User_ID = ?", newName, newEmail, userId);
        audit(userId, emailChanged ? "Profile updated by the account holder (name and email)" : "Profile updated by the account holder (name)", userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", newName);
        result.put("email", newEmail);
        return result;
    }

    /** The profile photo's media key (null removes it); answers the new URL and the old key to delete. */
    public Map<String, Object> setPhoto(int userId, String key) {
        String previous = (String) account(userId).get("Photo");
        String chosen = key == null || key.isBlank() ? null : key.strip();
        if (chosen != null) {
            Matcher m = PHOTO_KEY.matcher(chosen);
            if (!m.matches() || Integer.parseInt(m.group(1)) != userId) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That photo can't be used.");
            }
        }
        jdbcTemplate.update("UPDATE USERS SET Photo = ? WHERE User_ID = ?", chosen, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("photoUrl", photoUrl(chosen));
        result.put("previous", previous);
        return result;
    }

    /**
     * "Change my password": the current one, then a new one of the person's
     * own. Every other sign-in of the account ends; the device asking gets a
     * new token so it stays signed in.
     */
    @Transactional
    public Map<String, Object> changePassword(int userId, String currentPassword, String newPassword, String authHeader) {
        Map<String, Object> row = account(userId);
        String hash = (String) row.get("Password");
        if (hash == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This account signs in with Google, so it has no password to change.");
        }
        checkPassword(userId, hash, currentPassword, "Your current password isn't right.");
        checkNewPassword(newPassword, (String) row.get("User_Name"));
        if (passwordEncoder.matches(newPassword, hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password different from the current one.");
        }
        // Read before the change: afterwards this very token no longer counts.
        JwtService.TokenClaims claims = jwtService.parse(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        // Whole seconds, from here (not the database's clock): the new token
        // below carries this same second, and MySQL would round a fraction up.
        Instant changedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update(
            "UPDATE USERS SET Password = ?, Must_Change_Password = FALSE, Password_Changed_At = ? WHERE User_ID = ?",
            passwordEncoder.encode(newPassword), Timestamp.from(changedAt), userId
        );
        accountSecurity.forget(userId);
        audit(userId, "Password changed by the account holder; other sign-ins ended", userId);
        String token = claims.computerId() != null ? jwtService.issueComputerToken(userId, claims.computerId())
            : jwtService.issueToken(userId, claims.deviceId());
        return Map.of("token", token);
    }

    /** Unlocking the desktop app after the idle lock: is this the account's password? */
    public Map<String, Object> verifyPassword(int userId, String password) {
        String hash = (String) account(userId).get("Password");
        if (hash == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account has no password - sign in again.");
        }
        checkPassword(userId, hash, password, "That password isn't right.");
        return Map.of("ok", true);
    }

    /**
     * An admin's "Reset password": a temporary password, shown once to the
     * admin, which the person must replace at their next sign-in (until then
     * the account can do nothing else - PasswordChangeFilter). Every sign-in
     * of the account ends. Only the SuperAdmin resets an Admin; the
     * SuperAdmin's own password is changed only from their profile.
     */
    @Transactional
    public Map<String, Object> resetPassword(int targetUserId, int actorUserId) {
        if (targetUserId == actorUserId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change your own password from your profile.");
        }
        Map<String, Object> row = account(targetUserId);
        String targetRole = roleService.roleNameOf(targetUserId);
        String actorRole = roleService.roleNameOf(actorUserId);
        if (RoleName.SuperAdmin.name().equals(targetRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The SuperAdmin's password is changed only by the SuperAdmin, from their profile.");
        }
        if (RoleName.Admin.name().equals(targetRole) && !RoleName.SuperAdmin.name().equals(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the SuperAdmin can reset an Admin's password.");
        }
        String temporary = temporaryPassword();
        jdbcTemplate.update(
            "UPDATE USERS SET Password = ?, Must_Change_Password = TRUE, Password_Changed_At = ? WHERE User_ID = ? AND Is_Deleted = FALSE",
            passwordEncoder.encode(temporary), Timestamp.from(Instant.now().truncatedTo(ChronoUnit.SECONDS)), targetUserId
        );
        accountSecurity.forget(targetUserId);
        audit(targetUserId, "Password reset by an admin - to be changed at the next sign-in; all sign-ins ended", actorUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userName", row.get("User_Name"));
        result.put("temporaryPassword", temporary);
        return result;
    }

    // "Use at least 8 characters, with letters and numbers" - and not the username.
    private static void checkNewPassword(String password, String userName) {
        String p = password == null ? "" : password;
        if (p.length() < 8 || p.length() > 100 || !p.matches(".*[A-Za-z].*") || !p.matches(".*[0-9].*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use at least 8 characters, with letters and numbers.");
        }
        if (userName != null && userName.length() >= 3
            && p.toLowerCase(Locale.ROOT).contains(userName.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Don't use your username in your password.");
        }
    }

    private void checkPassword(int userId, String hash, String given, String wrongMessage) {
        long now = System.currentTimeMillis();
        Deque<Long> wrong = wrongPasswords.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        wrong.removeIf(at -> now - at > WRONG_WINDOW_MS);
        if (wrong.size() >= MAX_WRONG) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many wrong passwords. Please wait 15 minutes, or sign out and in again.");
        }
        if (given == null || !passwordEncoder.matches(given, hash)) {
            wrong.addLast(now);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, wrongMessage);
        }
        wrong.clear();
    }

    private String temporaryPassword() {
        while (true) {
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < TEMP_LENGTH; i++) {
                p.append(TEMP_ALPHABET.charAt(random.nextInt(TEMP_ALPHABET.length())));
            }
            String s = p.toString();
            if (s.matches(".*[A-Za-z].*") && s.matches(".*[0-9].*")) {
                return s;
            }
        }
    }

    private Map<String, Object> account(int userId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT User_ID, User_Name, Email, Password, Photo FROM USERS WHERE User_ID = ? AND Is_Deleted = FALSE", userId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
    }

    private void audit(int userId, String payload, int actorUserId) {
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES ('USERS', ?, ?, CURDATE(), ?)",
            userId, payload, actorUserId
        );
    }

}
