package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/audit ──────────────────────────────────────────

    @Test
    void queryAuditLog_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/audit", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void queryAuditLog_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/audit", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void queryAuditLog_asTeacher_returns403() {
        HttpClient client = teacherClient();
        ResponseEntity<Map> response = client.get("/api/admin/audit", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void queryAuditLog_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/audit", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void queryAuditLog_withFilters_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get(
                "/api/admin/audit?action=CREATE_USER&page=0&size=10", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
}
