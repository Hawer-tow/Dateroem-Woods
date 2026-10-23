package com.dateroem.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.dto.CreateUserRequest;
import com.dateroem.backend.dto.UpdateUserRequest;
import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.util.JsonKeys;

/**
 * User management for the desktop control app - staff/customer accounts
 * across the whole system, not scoped to any one client. Backs "one can
 * even disable a certain phone user" - Status now actually gates login
 * (see AuthService), this is where an admin flips it.
 */
@Service
public class UserService {

    private static final String SELECT_COLUMNS =
        "u.User_ID, u.User_Name, u.Name, u.Email, u.Phone_NO, u.Phone_Verified_At, u.Status, u.Role_ID, r.Name AS Role_Name, u.Can_Sync_Sms, " +
        "u.Must_Change_Password";
    private static final String FROM_JOIN = "FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID WHERE u.Is_Deleted = FALSE";

    private final JdbcTemplate jdbcTemplate;
    private final RoleService roleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(JdbcTemplate jdbcTemplate, RoleService roleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleService = roleService;
    }

    // limit=null means "all" - "specifying all, only 50 etc" applies here
    // the same way it does to the raw table browser.
    public List<Map<String, Object>> list(Integer limit) {
        String sql = "SELECT " + SELECT_COLUMNS + " " + FROM_JOIN + " ORDER BY u.User_ID DESC";
        List<Map<String, Object>> rows = limit == null
            ? jdbcTemplate.queryForList(sql)
            : jdbcTemplate.queryForList(sql + " LIMIT ?", limit);
        return JsonKeys.camelCaseKeys(rows);
    }

    public Map<String, Object> get(int userId) {
        try {
            return JsonKeys.camelCaseKeys(jdbcTemplate.queryForMap(
                "SELECT " + SELECT_COLUMNS + " " + FROM_JOIN + " AND u.User_ID = ?", userId
            ));
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    // "updating the number in the payment in any send money should also
    // show the change [in] the name ... a payment paid to a salary to a
    // user whose number is saved in the system [should show] that name" -
    // resolves whoever a typed phone number belongs to, for the payout
    // confirmation screen. Stored numbers aren't in one consistent format
    // (07.../254.../+254...), so the match compares the last 9 digits of
    // both sides instead of an exact string match. Only a name and id are
    // returned - not the full user record (email, role, bank details).
    public Map<String, Object> findByPhone(String phone) {
        String needle = last9Digits(phone);
        if (needle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that phone number");
        }
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
            "SELECT User_ID, Name, Phone_NO FROM USERS WHERE Is_Deleted = FALSE AND Phone_NO IS NOT NULL"
        );
        for (Map<String, Object> row : candidates) {
            if (needle.equals(last9Digits((String) row.get("Phone_NO")))) {
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("userId", row.get("User_ID"));
                result.put("name", row.get("Name"));
                return result;
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that phone number");
    }

    // "connect major expense to a user not just who created it ... who
    // its meant for" - a minimal id+name list for the mobile "For User"
    // picker, open to any signed-in user the same way lookup-by-phone is
    // (not the full admin user-management list()/get() above).
    public List<Map<String, Object>> listForPicker() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList(
            "SELECT User_ID, Name FROM USERS WHERE Is_Deleted = FALSE ORDER BY Name"
        ));
    }

    private static String last9Digits(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 9) return null;
        return digits.substring(digits.length() - 9);
    }

    // Staff accounts (marketer, products_staff, worker, manager, admin) -
    // unlike /api/auth/register (public signup, always "Customer"), this
    // lets an admin hand out any role directly, except SuperAdmin: there is
    // only ever one, and it moves only by being handed over (update()).
    public int create(CreateUserRequest request, int actorUserId) {
        if (request.roleId() == roleId(RoleName.SuperAdmin)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "There can only be one SuperAdmin. Create the account with another role - the SuperAdmin can hand the role over later.");
        }
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USERS WHERE User_Name = ? AND Is_Deleted = FALSE",
            Integer.class, request.userName()
        );
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken");
        }

        String hashed = passwordEncoder.encode(request.password());
        jdbcTemplate.update(
            "INSERT INTO USERS (User_Name, Name, Password, Email, Phone_NO, Status, Role_ID) VALUES (?, ?, ?, ?, ?, 1, ?)",
            request.userName(), request.name(), hashed, request.email(), request.phoneNo(), request.roleId()
        );
        int userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);

        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "USERS", userId, "Account created", actorUserId
        );
        return userId;
    }

    @Transactional
    public void update(int userId, UpdateUserRequest request, int actorUserId) {
        boolean handOver = checkSuperAdminChange(userId, request.roleId(), actorUserId);
        String oldUserName = changedUserName(userId, request.userName(), actorUserId);
        List<Object> params = new java.util.ArrayList<>();
        List<String> sets = new java.util.ArrayList<>();
        if (oldUserName != null) {
            sets.add("User_Name = ?");
            params.add(request.userName().strip());
        }
        if (request.name() != null) {
            sets.add("Name = ?");
            params.add(request.name());
        }
        if (request.email() != null) {
            sets.add("Email = ?");
            params.add(request.email());
        }
        if (request.phoneNo() != null) {
            sets.add("Phone_NO = ?");
            params.add(request.phoneNo());
            // A number typed in by an admin hasn't been confirmed by an SMS
            // code (migration 036): the customer confirms it at their next
            // checkout. The same number written differently stays confirmed.
            String oldPhone = jdbcTemplate.queryForObject("SELECT Phone_NO FROM USERS WHERE User_ID = ?", String.class, userId);
            String oldLast9 = last9Digits(oldPhone);
            if (oldLast9 == null || !oldLast9.equals(last9Digits(request.phoneNo()))) {
                sets.add("Phone_Verified_At = NULL");
            }
        }
        if (request.roleId() != null) {
            sets.add("Role_ID = ?");
            params.add(request.roleId());
        }
        if (request.canSyncSms() != null) {
            // "only one phone the company phone will have the ability to
            // read the offline SMS messages" - enforced here, not just
            // assumed: granting it to this account clears it from
            // whoever had it before, so it's never two at once.
            if (request.canSyncSms()) {
                jdbcTemplate.update("UPDATE USERS SET Can_Sync_Sms = FALSE WHERE Can_Sync_Sms = TRUE AND User_ID != ?", userId);
            }
            sets.add("Can_Sync_Sms = ?");
            params.add(request.canSyncSms());
        }
        if (sets.isEmpty()) {
            return;
        }
        params.add(userId);
        int updated = jdbcTemplate.update(
            "UPDATE USERS SET " + String.join(", ", sets) + " WHERE User_ID = ? AND Is_Deleted = FALSE",
            params.toArray()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "USERS", userId, "Account details updated", actorUserId
        );
        if (oldUserName != null) {
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
                "USERS", userId, "Username changed from \"" + oldUserName + "\" to \"" + request.userName().strip() + "\"", actorUserId
            );
        }
        if (handOver) {
            // The one who handed the role over carries on as an Admin.
            jdbcTemplate.update("UPDATE USERS SET Role_ID = ? WHERE User_ID = ?", roleId(RoleName.Admin), actorUserId);
            jdbcTemplate.update(
                "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
                "USERS", userId, "SuperAdmin role handed over to this account; the previous SuperAdmin is now an Admin", actorUserId
            );
        }
    }

    /**
     * "there can only be one super admin ... the one who permits phones
     * desktops is the superadmin" - so only the SuperAdmin may change the
     * SuperAdmin account or give the role away, and there is always exactly
     * one. Giving the role to someone else hands it over: they become the
     * SuperAdmin and the giver becomes an Admin (returns true).
     */
    private boolean checkSuperAdminChange(int userId, Integer newRoleId, int actorUserId) {
        boolean targetIsSuper = isSuperAdmin(userId);
        boolean actorIsSuper = isSuperAdmin(actorUserId);
        if (targetIsSuper && !actorIsSuper) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the SuperAdmin can change the SuperAdmin's account.");
        }
        if (newRoleId == null) {
            return false;
        }
        boolean becomesSuper = newRoleId == roleId(RoleName.SuperAdmin);
        if (targetIsSuper && !becomesSuper) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "There must always be one SuperAdmin - hand the role over by giving another account the SuperAdmin role.");
        }
        if (becomesSuper && !targetIsSuper) {
            if (!actorIsSuper) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the SuperAdmin can hand over the SuperAdmin role.");
            }
            return true;
        }
        return false;
    }

    /**
     * "in users for super admin allow them to edit a username for a user,
     * since one might have used a wrong username when creating" - only the
     * SuperAdmin, and the new one follows the sign-up rule (3 to 30
     * characters, no spaces, no @) and is not taken. Returns the old
     * username when it really changes, null when it stays the same (the
     * edit form sends it back unchanged on every save).
     */
    private String changedUserName(int userId, String requested, int actorUserId) {
        if (requested == null) {
            return null;
        }
        String userName = requested.strip();
        String current;
        try {
            current = jdbcTemplate.queryForObject(
                "SELECT User_Name FROM USERS WHERE User_ID = ? AND Is_Deleted = FALSE", String.class, userId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (userName.equals(current)) {
            return null;
        }
        if (!isSuperAdmin(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the SuperAdmin can change a username.");
        }
        if (!AuthService.USER_NAME.matcher(userName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose a username of 3 to 30 characters, with no spaces and no @.");
        }
        Integer taken = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USERS WHERE User_Name = ? AND Is_Deleted = FALSE AND User_ID <> ?", Integer.class, userName, userId
        );
        if (taken != null && taken > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken");
        }
        return current;
    }

    private boolean isSuperAdmin(int userId) {
        return RoleName.SuperAdmin.name().equals(roleService.roleNameOf(userId));
    }

    private int roleId(RoleName role) {
        return jdbcTemplate.queryForObject("SELECT Role_ID FROM ROLE WHERE Name = ?", Integer.class, role.name());
    }

    public void setStatus(int userId, boolean active, int actorUserId) {
        if (!active && isSuperAdmin(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The SuperAdmin account can't be disabled - hand the SuperAdmin role to another account first.");
        }
        int updated = jdbcTemplate.update(
            "UPDATE USERS SET Status = ? WHERE User_ID = ? AND Is_Deleted = FALSE",
            active, userId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        jdbcTemplate.update(
            "INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID) VALUES (?, ?, ?, CURDATE(), ?)",
            "USERS", userId, active ? "Account enabled" : "Account disabled", actorUserId
        );
    }
}
