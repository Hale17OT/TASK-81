package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/notifications ───────────────────────────────────────

    @Test
    void listNotifications_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/notifications", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listNotifications_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/notifications", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── GET /api/notifications/unread-count ──────────────────────────

    @Test
    void unreadCount_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get(
                "/api/notifications/unread-count", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void unreadCount_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/notifications/unread-count", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/notifications/read-all ──────────────────────────────

    @Test
    void markAllRead_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put(
                "/api/notifications/read-all", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void markAllRead_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/notifications/read-all",
                org.springframework.http.HttpMethod.PUT,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
