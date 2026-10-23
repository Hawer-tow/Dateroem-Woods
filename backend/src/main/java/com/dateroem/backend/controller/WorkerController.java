package com.dateroem.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dateroem.backend.security.AdminGuard;
import com.dateroem.backend.security.JwtService;
import com.dateroem.backend.service.WorkerService;

// Worker details and documents (migration 040). The worker's own (/me) from
// the phone; the list of where everyone stands for Admins; everything else -
// reading the numbers and photos, verifying, deleting, adding fields - the
// SuperAdmin only ("SuperAdmin only", 24 October 2026).
@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService workerService;
    private final AdminGuard adminGuard;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public WorkerController(WorkerService workerService, AdminGuard adminGuard, JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.workerService = workerService;
        this.adminGuard = adminGuard;
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me")
    public Map<String, Object> mine(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        return workerService.mine(activeUser(authHeader));
    }

    // {"values": {"id_number": "...", ...}} - a blank value removes it.
    @PutMapping("/me")
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveMine(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                        @RequestBody Map<String, Object> body) {
        Object values = body == null ? null : body.get("values");
        return workerService.saveMine(activeUser(authHeader), values instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of());
    }

    // {"data": "<base64 JPEG>"} - the phone server has already shrunk it.
    @PostMapping("/me/documents/{typeKey}")
    public Map<String, Object> uploadMine(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @PathVariable String typeKey, @RequestBody Map<String, String> body) {
        return workerService.uploadMine(activeUser(authHeader), typeKey, body == null ? null : body.get("data"));
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        adminGuard.requireAdmin(authHeader);
        return workerService.list();
    }

    @GetMapping("/definitions")
    public Map<String, Object> definitions(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        adminGuard.requireAdmin(authHeader);
        return workerService.definitions();
    }

    // {label, kind (text|number|date|phone), hint, sensitive, required}
    @PostMapping("/fields")
    public Map<String, Object> addField(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                        @RequestBody Map<String, Object> body) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        Map<String, Object> b = body == null ? Map.of() : body;
        return workerService.addField((String) b.get("label"), (String) b.get("kind"), (String) b.get("hint"),
            Boolean.TRUE.equals(b.get("sensitive")), Boolean.TRUE.equals(b.get("required")), actor);
    }

    @PatchMapping("/fields/{fieldKey}")
    public Map<String, Object> setFieldActive(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                              @PathVariable String fieldKey, @RequestBody Map<String, Boolean> body) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        return workerService.setActive("WORKER_FIELDS", "Field_Key", fieldKey, body != null && Boolean.TRUE.equals(body.get("active")), actor);
    }

    // {label, hint, required}
    @PostMapping("/doc-types")
    public Map<String, Object> addDocType(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @RequestBody Map<String, Object> body) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        Map<String, Object> b = body == null ? Map.of() : body;
        return workerService.addDocType((String) b.get("label"), (String) b.get("hint"), Boolean.TRUE.equals(b.get("required")), actor);
    }

    @PatchMapping("/doc-types/{typeKey}")
    public Map<String, Object> setDocTypeActive(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                @PathVariable String typeKey, @RequestBody Map<String, Boolean> body) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        return workerService.setActive("WORKER_DOC_TYPES", "Type_Key", typeKey, body != null && Boolean.TRUE.equals(body.get("active")), actor);
    }

    @GetMapping("/{userId}")
    public Map<String, Object> detail(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @PathVariable int userId) {
        return workerService.detail(userId, adminGuard.requireSuperAdmin(authHeader));
    }

    @GetMapping("/{userId}/documents/{documentId}")
    public Map<String, Object> document(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                        @PathVariable int userId, @PathVariable int documentId) {
        return workerService.document(userId, documentId, adminGuard.requireSuperAdmin(authHeader));
    }

    // {status: VERIFIED | NEEDS_CHANGES, note}
    @PostMapping("/{userId}/verify")
    public Map<String, Object> verify(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @PathVariable int userId, @RequestBody Map<String, String> body) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        Map<String, String> b = body == null ? Map.of() : body;
        return workerService.verify(userId, b.get("status"), b.get("note"), actor);
    }

    @DeleteMapping("/{userId}/documents/{documentId}")
    public Map<String, Object> deleteDocument(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                              @PathVariable int userId, @PathVariable int documentId) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        workerService.deleteDocument(userId, documentId, actor);
        return Map.of("status", "deleted");
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> deleteAll(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                         @PathVariable int userId) {
        int actor = adminGuard.requireSuperAdmin(authHeader);
        workerService.deleteAll(userId, actor);
        return Map.of("status", "deleted");
    }

    private int activeUser(String authHeader) {
        int userId = jwtService.validate(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM USERS WHERE User_ID = ? AND Status = 1 AND Is_Deleted = FALSE", Integer.class, userId
        );
        if (active == null || active == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled");
        }
        return userId;
    }
}
