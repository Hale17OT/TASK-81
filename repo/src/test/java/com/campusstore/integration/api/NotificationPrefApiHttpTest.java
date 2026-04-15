package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPrefApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/notification-preferences ─────────────────────────────

    @Test
    void getNotificationPreferences_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get(
                "/api/notification-preferences", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertNotNull(response.getBody().get("data"),
                "Notification preferences data must not be null");
    }

    @Test
    void getNotificationPreferences_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/notification-preferences", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/notification-preferences ─────────────────────────────

    @Test
    void updateNotificationPreferences_authenticated_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> pref = Map.of(
                "type", "REQUEST_SUBMITTED",
                "enabled", true,
                "emailEnabled", false
        );
        Map<String, Object> req = Map.of("preferences", List.of(pref));

        ResponseEntity<Map> response = client.put(
                "/api/notification-preferences", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));

        // Verify persistence: GET-after-PUT must return non-null data
        ResponseEntity<Map> getResp = client.get("/api/notification-preferences", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode(),
                "GET preferences after update must succeed");
        assertNotNull(getResp.getBody().get("data"),
                "Preferences data must be readable after update");
    }
}
