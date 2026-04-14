package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowsingApiHttpTest extends BaseHttpApiTest {

    // ── POST /api/browsing-history/{itemId} ───────────────────────────

    @Test
    void recordBrowse_authenticated_returns200() {
        HttpClient client = studentClient();
        // Use item id 1, created by TestDataConfig
        ResponseEntity<Map> response = client.post(
                "/api/browsing-history/1", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── GET /api/favorites ────────────────────────────────────────────

    @Test
    void listFavorites_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/favorites", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertNotNull(response.getBody().get("data"));
    }

    // ── POST /api/favorites/{itemId} ──────────────────────────────────

    @Test
    void addFavorite_authenticated_returns200() {
        HttpClient client = teacherClient();
        ResponseEntity<Map> response = client.post(
                "/api/favorites/1", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── DELETE /api/favorites/{itemId} ────────────────────────────────

    @Test
    void removeFavorite_afterAdding_returns200() {
        HttpClient client = adminClient();

        // Add favorite first
        client.post("/api/favorites/1", null, Map.class);

        // Then remove it
        ResponseEntity<Map> response = client.delete(
                "/api/favorites/1", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void favorites_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/favorites", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
