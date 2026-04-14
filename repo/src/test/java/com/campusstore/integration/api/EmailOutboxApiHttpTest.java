package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailOutboxApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/email-outbox ───────────────────────────────────

    @Test
    void listEmailOutbox_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/email-outbox", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listEmailOutbox_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/email-outbox", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listEmailOutbox_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/email-outbox", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/admin/email-outbox/export ───────────────────────────

    @Test
    void exportEmailOutbox_asAdmin_returnsZip() {
        HttpClient client = adminClient();
        ResponseEntity<byte[]> response = client.post(
                "/api/admin/email-outbox/export", null, byte[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // ZIP files are returned with content; verify non-empty
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void exportEmailOutbox_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<String> response = client.post(
                "/api/admin/email-outbox/export", null, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
