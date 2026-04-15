package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void updatePolicy_asAdmin_isAccessible() {
        HttpClient client = adminClient();
        Map<String, Object> req = Map.of("retentionDays", 90);
        ResponseEntity<Map> response = client.put(
                "/api/admin/policies/search_log", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to update a retention policy");
        assertTrue((Boolean) response.getBody().get("success"),
                "Update policy response must have success=true");
        // Verify the response returns the updated entity with the new value
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data, "Update policy response must return the updated policy entity");
        assertEquals(90, ((Number) data.get("retentionDays")).intValue(),
                "PUT response must reflect the new retentionDays=90");

        // Verify the change persisted: GET-all must show search_log with retentionDays=90
        ResponseEntity<Map> listResp = client.get("/api/admin/policies", Map.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        List<Map<String, Object>> policies = (List<Map<String, Object>>) listResp.getBody().get("data");
        assertNotNull(policies, "Policy list must not be null after update");
        Map<String, Object> searchLogPolicy = null;
        for (Map<String, Object> p : policies) {
            if ("search_log".equals(p.get("entityType"))) {
                searchLogPolicy = p;
                break;
            }
        }
        assertNotNull(searchLogPolicy, "search_log policy must appear in GET /api/admin/policies after update");
        assertEquals(90, ((Number) searchLogPolicy.get("retentionDays")).intValue(),
                "Persisted search_log retentionDays must be 90 after update");
    }
}
