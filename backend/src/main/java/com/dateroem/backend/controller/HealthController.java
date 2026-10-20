package com.dateroem.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "backend");

        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("status", "ok");
            body.put("db", Integer.valueOf(1).equals(result) ? "connected" : "unexpected_result");
        } catch (Exception e) {
            body.put("status", "degraded");
            body.put("db", "unreachable");
            body.put("error", e.getMessage());
        }

        return body;
    }

}
