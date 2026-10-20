package com.dateroem.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dateroem.backend.model.RoleName;
import com.dateroem.backend.util.JsonKeys;

@Service
public class RoleService {

    private static final Set<String> ADMIN_ROLES = Set.of(RoleName.Admin.name(), RoleName.SuperAdmin.name());

    private final JdbcTemplate jdbcTemplate;

    public RoleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list() {
        return JsonKeys.camelCaseKeys(jdbcTemplate.queryForList("SELECT Role_ID, Name FROM ROLE WHERE Is_Deleted = FALSE"));
    }

    // Real server-side privilege check - the mobile/desktop clients also
    // gate their UI on role, but that alone isn't enough for anything that
    // moves money (business rules concern: "a malicious user can prompt
    // ... for the wrong reason or category").
    public String roleNameOf(int userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT r.Name FROM USERS u JOIN ROLE r ON r.Role_ID = u.Role_ID " +
                "WHERE u.User_ID = ? AND u.Is_Deleted = FALSE",
                String.class, userId
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean isAdmin(int userId) {
        String roleName = roleNameOf(userId);
        return roleName != null && ADMIN_ROLES.contains(roleName);
    }

    public boolean isSuperAdmin(int userId) {
        return RoleName.SuperAdmin.name().equals(roleNameOf(userId));
    }

    // Staff = anyone who is not a Customer. The phone app is the staff app
    // (the website is the customer channel), so the mobile sign-in uses this
    // to turn away customer accounts before a phone is ever recorded for
    // them - a customer must not be able to reach staff screens or data by
    // signing in on the phone app.
    public boolean isStaff(int userId) {
        String roleName = roleNameOf(userId);
        return roleName != null && !RoleName.Customer.name().equals(roleName);
    }
}
