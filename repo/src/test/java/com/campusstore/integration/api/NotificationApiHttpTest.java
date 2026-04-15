package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/notifications ───────────────────────────────────────

    @Test
    void listNotifications_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/notifications", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data, "Notification list data must not be null");
        assertNotNull(data.get("content"), "Notification list must include a content array");
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

    // ── PUT /api/notifications/{id}/read ─────────────────────────────

    @Test
    void markOneRead_authenticated_isAccessible() {
        HttpClient admin = adminClient();
        HttpClient student = studentClient();

        // Create an item and request to trigger a REQUEST_SUBMITTED notification
        // for the student. This ensures a real notification exists to mark as read.
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Notification Test Item " + ts);
        itemReq.put("sku", "NT-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 3.99);
        itemReq.put("quantity", 5);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(),
                "Item creation must succeed before notification test");

        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Notification test request");
        ResponseEntity<Map> reqResp = student.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, reqResp.getStatusCode(),
                "Request creation must succeed to generate REQUEST_SUBMITTED notification");

        // List student's notifications — must now contain at least one entry
        ResponseEntity<Map> listResp = student.get("/api/notifications", Map.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode(),
                "Notification list must return 200");
        Map<String, Object> pageData = (Map<String, Object>) listResp.getBody().get("data");
        assertNotNull(pageData, "Notification list data must not be null");
        List<Map<String, Object>> content = (List<Map<String, Object>>) pageData.get("content");
        assertNotNull(content, "Notification list content must not be null");
        assertTrue(content.size() > 0,
                "Student must have at least one notification after submitting a request");

        // Mark the first notification as read — must succeed with 200
        Long notificationId = ((Number) content.get(0).get("id")).longValue();
        ResponseEntity<Map> markResp = student.put(
                "/api/notifications/" + notificationId + "/read", null, Map.class);
        assertEquals(HttpStatus.OK, markResp.getStatusCode(),
                "Marking an owned notification as read must return 200");
        assertTrue((Boolean) markResp.getBody().get("success"),
                "Mark-one-read response must have success=true");
    }

    @Test
    void markOneRead_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/notifications/1/read",
                org.springframework.http.HttpMethod.PUT,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
