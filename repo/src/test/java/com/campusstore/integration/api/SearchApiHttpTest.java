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
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Search endpoint must be publicly accessible");
        assertTrue((Boolean) response.getBody().get("success"),
                "Search response must have success=true");
        assertNotNull(response.getBody().get("data"),
                "Search response data must not be null");
    }

    @Test
    void search_withKeyword_isAccessible() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/search?q=test", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Keyword search must be publicly accessible");
        assertTrue((Boolean) response.getBody().get("success"),
                "Keyword search response must have success=true");
    }

    @Test
    void search_authenticated_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/search?q=item", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Authenticated search must return 200");
        assertTrue((Boolean) response.getBody().get("success"),
                "Authenticated search response must have success=true");
        assertNotNull(response.getBody().get("data"),
                "Search data must not be null");
    }

    // ── GET /api/search/trending (public) ────────────────────────────
    @Test
    void trending_isAccessible() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/search/trending", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Trending endpoint must be publicly accessible");
        assertTrue((Boolean) response.getBody().get("success"),
                "Trending response must have success=true");
    }

    // ── GET /api/search/history (authenticated) ──────────────────────
    @Test
    void history_authenticated_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/search/history", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Search history must return 200 for authenticated user");
        assertTrue((Boolean) response.getBody().get("success"),
                "Search history response must have success=true");
    }

    @Test
    void history_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/search/history", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
