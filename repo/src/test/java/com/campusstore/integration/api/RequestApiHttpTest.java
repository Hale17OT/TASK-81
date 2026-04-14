package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestApiHttpTest extends BaseHttpApiTest {

    // ── POST /api/requests ──────────────────────────────────────────
    @Test
    void createRequest_asStudent_isAccessible() {
        HttpClient client = studentClient();
        Map<String, Object> req = new HashMap<>();
        req.put("itemId", 1);
        req.put("quantity", 1);
        req.put("justification", "Need for class");
        ResponseEntity<Map> response = client.post("/api/requests", req, Map.class);
        // Should reach the endpoint (not blocked by auth)
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void createRequest_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/requests",
                Map.of("itemId", 1, "quantity", 1), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── GET /api/requests/mine ──────────────────────────────────────
    @Test
    void listMyRequests_asStudent_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/requests/mine", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void listMyRequests_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/requests/mine", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── GET /api/requests/pending-approval ───────────────────────────
    @Test
    void pendingApproval_asTeacher_isAccessible() {
        HttpClient client = teacherClient();
        ResponseEntity<Map> response = client.get("/api/requests/pending-approval", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void pendingApproval_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/requests/pending-approval", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void pendingApproval_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/requests/pending-approval", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── GET /api/requests/{id} ──────────────────────────────────────
    @Test
    void getRequest_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/requests/1", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/requests/{id}/approve ───────────────────────────────
    @Test
    void approveRequest_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put("/api/requests/1/approve", null, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/requests/{id}/reject ───────────────────────────────
    @Test
    void rejectRequest_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put("/api/requests/1/reject",
                Map.of("reason", "test"), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/requests/{id}/cancel ───────────────────────────────
    @Test
    void cancelRequest_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange("/api/requests/1/cancel",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of()), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/requests/{id}/picked-up ─────────────────────────────
    @Test
    void pickedUp_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put("/api/requests/1/picked-up", null, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void pickedUp_asAdmin_isAccessible() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.put("/api/requests/1/picked-up", null, Map.class);
        // Should reach endpoint (not auth blocked), may return 404/409/500 depending on data
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }
}
