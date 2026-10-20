package com.dateroem.backend.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts raw JDBC row maps (PascalCase_With_Underscores column names,
 * straight off a `SELECT *`) into camelCase JSON, so every endpoint
 * returns a consistent shape whether it's backed by a typed DTO or a plain
 * query. Also strips internal soft-delete bookkeeping columns
 * (Is_Deleted/Deleted_By/Deleted_At) that API consumers don't need.
 */
public final class JsonKeys {

    private static final Set<String> INTERNAL_COLUMNS = Set.of("Is_Deleted", "Deleted_By", "Deleted_At");

    private JsonKeys() {
    }

    public static String toCamelCase(String column) {
        String[] parts = column.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            result.append(i == 0 ? lowerFirstSegment(part) : capitalizeSegment(part));
        }
        return result.toString();
    }

    public static Map<String, Object> camelCaseKeys(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (INTERNAL_COLUMNS.contains(entry.getKey())) {
                continue;
            }
            result.put(toCamelCase(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static List<Map<String, Object>> camelCaseKeys(List<Map<String, Object>> rows) {
        return rows.stream().map(JsonKeys::camelCaseKeys).collect(Collectors.toList());
    }

    private static String lowerFirstSegment(String part) {
        if (isAllUpper(part)) {
            return part.toLowerCase();
        }
        return Character.toLowerCase(part.charAt(0)) + part.substring(1);
    }

    private static String capitalizeSegment(String part) {
        if (isAllUpper(part)) {
            return Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase();
        }
        return Character.toUpperCase(part.charAt(0)) + part.substring(1);
    }

    private static boolean isAllUpper(String part) {
        return part.length() > 1 && part.equals(part.toUpperCase());
    }
}
