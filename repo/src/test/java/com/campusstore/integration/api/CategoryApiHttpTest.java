package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/categories ─────────────────────────────────────

    @Test
    void listCategories_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/categories", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertNotNull(response.getBody().get("data"), "Category list data must not be null");
    }

    @Test
    void listCategories_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/categories", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listCategories_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/categories", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/admin/categories ────────────────────────────────────

    @Test
    void createCategory_asAdmin_returns200() {
        HttpClient client = adminClient();
        String categoryName = "New Category " + System.currentTimeMillis();
        Map<String, Object> req = Map.of(
                "name", categoryName,
                "description", "A category created by test"
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/categories", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"), "Created category must have an id");
        assertEquals(categoryName, data.get("name"),
                "Response must echo the submitted category name");
    }

    @Test
    void createCategory_asStudent_returns403() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of(
                "name", "Forbidden Category",
                "description", "Should be denied"
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/categories", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
