package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/search (public, no auth needed) ────────────────────
    @Test
    void search_unauthenticated_isAccessible() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/search", Map.class);
        // Search is public — should NOT return 401 or 403
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void search_withKeyword_isAccessible() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/search?q=test", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    @Test
    void search_authenticated_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/search?q=item", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    // ── GET /api/search/trending (public) ────────────────────────────
    @Test
    void trending_isAccessible() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/search/trending", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    // ── GET /api/search/history (authenticated) ──────────────────────
    @Test
    void history_authenticated_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/search/history", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void history_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/search/history", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
