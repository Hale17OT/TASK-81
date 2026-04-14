package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartmentApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/departments ────────────────────────────────────

    @Test
    void listDepartments_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/departments", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listDepartments_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/departments", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listDepartments_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/departments", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/admin/departments ───────────────────────────────────

    @Test
    void createDepartment_asAdmin_returns200() {
        HttpClient client = adminClient();

        Map<String, Object> req = Map.of(
                "name", "New Dept " + System.currentTimeMillis(),
                "description", "A department created by test"
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/departments", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    @Test
    void createDepartment_asStudent_returns403() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of(
                "name", "Forbidden Dept",
                "description", "Should be denied"
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/departments", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
