package com.dateroem.backend.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * "ticket number for tracking" (migration 038) - every order's public code,
 * like DW-7K3Q9P: what the customer sees, quotes on the phone and types into
 * Track my order, and what staff search by. Random rather than counted, so a
 * code says nothing about how many orders exist and the next one can't be
 * guessed; the letters that are easy to misread (0/O, 1/I/L) are left out.
 * Six characters from 31 give about 887 million codes.
 *
 * Orders made before the codes existed get one when the backend starts.
 */
@Component
public class OrderTickets implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderTickets.class);
    private static final String PREFIX = "DW-";
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 6;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    public OrderTickets(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Gives the order its ticket (once) and returns it. */
    public String assign(int orderId) {
        // A clash with an existing code is vanishingly rare; the unique key
        // catches it and another code is drawn.
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = newCode();
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE ORDERS SET Ticket_Code = ? WHERE Order_ID = ? AND Ticket_Code IS NULL", code, orderId
                );
                if (updated == 1) {
                    return code;
                }
                return jdbcTemplate.queryForObject("SELECT Ticket_Code FROM ORDERS WHERE Order_ID = ?", String.class, orderId);
            } catch (DuplicateKeyException clash) {
                // try another
            }
        }
        throw new IllegalStateException("Could not give order " + orderId + " a ticket code");
    }

    /**
     * What a customer typed, as a stored code: capitals, spaces and dashes
     * dropped, the DW prefix optional - "dw 7k3q9p", "7K3Q9P" and
     * "DW-7K3Q9P" all mean the same order. Null when it can't be a code.
     */
    public static String normalise(String typed) {
        if (typed == null) {
            return null;
        }
        String compact = typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (compact.startsWith("DW") && compact.length() == LENGTH + 2) {
            compact = compact.substring(2);
        }
        if (compact.length() != LENGTH) {
            return null;
        }
        for (char c : compact.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return PREFIX + compact;
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Integer> missing = jdbcTemplate.queryForList(
            "SELECT Order_ID FROM ORDERS WHERE Ticket_Code IS NULL ORDER BY Order_ID", Integer.class
        );
        for (int orderId : missing) {
            assign(orderId);
        }
        if (!missing.isEmpty()) {
            log.info("Gave {} older order(s) a ticket code", missing.size());
        }
    }
}
