package com.dateroem.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Raw database browser for the desktop control app - "one should be able
 * to even see the databases and the data in them by e.g specifying all,
 * only 50 etc". Deliberately generic (works for any table, current or
 * future) rather than one endpoint per table.
 *
 * SQL injection note: tableName is interpolated directly into every query
 * here, which would normally be unsafe - it's only safe because
 * assertRealTable() first checks it's an exact, case-sensitive match
 * against a table name MySQL itself reports via information_schema
 * (never a client-constructed string used as-is). Every call path goes
 * through that check before touching a query.
 */
@Service
public class AdminDataService {

    // Any table, current or future: the stored password hashes and the
    // phones'/computers' sign-in keys today (USERS.Password,
    // USER_DEVICES.Public_Key, USER_COMPUTERS.Public_Key), and whatever
    // secret-looking column comes later.
    private static final Pattern HIDDEN_COLUMN = Pattern.compile("(?i).*(password|secret|token|public_key).*");
    private static final String HIDDEN = "(hidden)";

    private final JdbcTemplate jdbcTemplate;

    public AdminDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> listTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY TABLE_NAME",
            String.class
        );
    }

    private void assertRealTable(String tableName) {
        if (!listTables().contains(tableName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such table");
        }
    }

    // Raw rows, exact column names, nothing stripped or renamed (unlike
    // every other endpoint's JsonKeys.camelCaseKeys) - the whole point of
    // this screen is showing precisely what's in the database, soft-delete
    // bookkeeping columns included - except the values of secret columns
    // (HIDDEN_COLUMN), which never leave the server now that the desktop
    // app can run on other admins' computers. "order ids by the last one e.g 50 then
    // 49 since those should be the latest ... i believe the ids will help
    // since they are integers" - newest-first by whatever the table's own
    // primary key is (found generically, same "works for any table"
    // approach as the rest of this class).
    public Map<String, Object> browse(String tableName, Integer limit, int offset) {
        assertRealTable(tableName);
        String primaryKey = primaryKeyColumn(tableName);
        String orderClause = primaryKey != null ? " ORDER BY `" + primaryKey + "` DESC" : "";
        String base = "SELECT * FROM `" + tableName + "`" + orderClause;
        List<Map<String, Object>> rows = limit == null
            ? jdbcTemplate.queryForList(base)
            : jdbcTemplate.queryForList(base + " LIMIT ? OFFSET ?", limit, offset);
        int total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + tableName + "`", Integer.class);
        rows.forEach(row -> row.replaceAll((column, value) ->
            value != null && HIDDEN_COLUMN.matcher(column).matches() ? HIDDEN : value));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", tableName);
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    // Only ever called with a tableName already proven real by
    // assertRealTable(), and the column name it returns comes from
    // information_schema itself (never client input), so interpolating it
    // into the ORDER BY clause carries the same safety guarantee as
    // tableName's own use elsewhere in this class.
    private String primaryKeyColumn(String tableName) {
        List<String> keys = jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY' " +
            "ORDER BY ORDINAL_POSITION",
            String.class, tableName
        );
        return keys.isEmpty() ? null : keys.get(0);
    }
}
