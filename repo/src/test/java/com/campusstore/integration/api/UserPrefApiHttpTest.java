package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrefApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/user/preferences ─────────────────────────────────────

    @Test
    void getUserPreferences_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/user/preferences", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Authenticated user must get 200 from preferences endpoint");
        assertTrue((Boolean) response.getBody().get("success"),
                "Preferences response must have success=true");
    }

    @Test
    void getUserPreferences_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/user/preferences", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/user/preferences/dnd ─────────────────────────────────

    @Test
    void setDnd_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of(
                "startTime", "22:00:00",
                "endTime", "07:00:00"
        );
        ResponseEntity<Map> response = client.put(
                "/api/user/preferences/dnd", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void clearDnd_nullTimes_returns200() {
        HttpClient client = teacherClient();

        // Both null clears DND
        Map<String, Object> req = new HashMap<>();
        req.put("startTime", null);
        req.put("endTime", null);
        ResponseEntity<Map> response = client.put(
                "/api/user/preferences/dnd", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── PUT /api/user/preferences/personalization ─────────────────────

    @Test
    void togglePersonalization_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of("enabled", false);
        ResponseEntity<Map> response = client.put(
                "/api/user/preferences/personalization", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
}
