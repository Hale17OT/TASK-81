package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/profile ──────────────────────────────────────────────

    @Test
    void getProfile_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("teststudent", data.get("username"));
    }

    @Test
    void getProfile_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/profile", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/profile ──────────────────────────────────────────────

    @Test
    void updateProfile_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> updateReq = Map.of(
                "displayName", "Updated Student Name",
                "email", "updatedstudent@campus.edu",
                "phone", "5559998888"
        );
        ResponseEntity<Map> response = client.put("/api/profile", updateReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── GET /api/profile/addresses ────────────────────────────────────

    @Test
    void listAddresses_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile/addresses", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── POST /api/profile/addresses ───────────────────────────────────

    @Test
    void addAddress_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> addressReq = Map.of(
                "label", "Home",
                "street", "123 Test Street",
                "city", "Testville",
                "state", "TS",
                "zipCode", "12345"
        );
        ResponseEntity<Map> response = client.post(
                "/api/profile/addresses", addressReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    // ── GET /api/profile/tags ─────────────────────────────────────────

    @Test
    void listTags_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile/tags", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── POST /api/profile/tags ────────────────────────────────────────

    @Test
    void addTag_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, String> tagReq = Map.of("tag", "electronics");
        ResponseEntity<Map> response = client.post(
                "/api/profile/tags", tagReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── DELETE /api/profile/tags/{tag} ────────────────────────────────

    @Test
    void removeTag_existingTag_returns200() {
        HttpClient client = teacherClient();

        // First add a tag
        Map<String, String> tagReq = Map.of("tag", "books");
        client.post("/api/profile/tags", tagReq, Map.class);

        // Then delete it
        ResponseEntity<Map> response = client.delete(
                "/api/profile/tags/books", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
}
