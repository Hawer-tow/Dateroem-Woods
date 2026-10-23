package com.dateroem.backend.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Each account's password state (migration 039), read by every token check:
 * when the password last changed - sign-ins made before then are refused, so
 * changing or resetting a password signs the account out everywhere else -
 * and whether an admin's reset is waiting to be replaced by a password of
 * the person's own (PasswordChangeFilter).
 *
 * Every request checks its token at least once, so the answer is kept for a
 * few seconds; a change made here is forgotten at once (forget()).
 */
@Component
public class AccountSecurity {

    public record State(Instant passwordChangedAt, boolean mustChangePassword) {}

    private record Cached(State state, long readAt) {}

    private static final long KEEP_MS = 15_000;
    private static final State NONE = new State(null, false);

    private final JdbcTemplate jdbcTemplate;
    private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

    public AccountSecurity(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public State state(int userId) {
        long now = System.currentTimeMillis();
        Cached cached = cache.get(userId);
        if (cached != null && now - cached.readAt() < KEEP_MS) {
            return cached.state();
        }
        // Read as a Timestamp, the way it was written: the connection runs in
        // UTC (serverTimezone=UTC) while this computer's clock is in Kenyan
        // time, and only the driver's own conversion gives back the same moment.
        List<State> rows = jdbcTemplate.query(
            "SELECT Password_Changed_At, Must_Change_Password FROM USERS WHERE User_ID = ?",
            (rs, i) -> {
                Timestamp changed = rs.getTimestamp("Password_Changed_At");
                return new State(changed == null ? null : changed.toInstant(), rs.getBoolean("Must_Change_Password"));
            },
            userId
        );
        State state = rows.isEmpty() ? NONE : rows.get(0);
        if (cache.size() > 10_000) {
            cache.clear();
        }
        cache.put(userId, new Cached(state, now));
        return state;
    }

    public void forget(int userId) {
        cache.remove(userId);
    }
}
