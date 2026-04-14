package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAdminApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/policies ──────────────────────────────────────

    @Test
    void listPolicies_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/policies", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listPolicies_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/policies", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listPolicies_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/policies", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/admin/policies/{entityType} ─────────────────────────

    @Test
    void updatePolicy_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> req = Map.of("retentionDays", 180);
        ResponseEntity<Map> response = client.put(
                "/api/admin/policies/search_log", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void updatePolicy_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/policies/search_log",
                org.springframework.http.HttpMethod.PUT,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
