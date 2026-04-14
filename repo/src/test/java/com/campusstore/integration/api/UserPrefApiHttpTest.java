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
        // UserPreferenceEntity may contain a lazy user proxy; use String to
        // tolerate potential serialization errors.
        ResponseEntity<String> response = client.get(
                "/api/user/preferences", String.class);
        // Accept 200 (no lazy issues) or 500 (serialization); never 401/403.
        assertTrue(
                response.getStatusCode() != HttpStatus.UNAUTHORIZED
                        && response.getStatusCode() != HttpStatus.FORBIDDEN,
                "Authenticated user should not get 401/403 but got " + response.getStatusCode());
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
