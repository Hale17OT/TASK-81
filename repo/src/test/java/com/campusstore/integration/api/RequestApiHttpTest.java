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
        // Item 1 (Arduino Uno R3 Board) has requiresApproval=true — request creation
        // goes to PENDING_APPROVAL without triggering pick-task creation (no location needed).
        Map<String, Object> req = new HashMap<>();
        req.put("itemId", 1);
        req.put("quantity", 1);
        req.put("justification", "Need for class");
        ResponseEntity<Map> response = client.post("/api/requests", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Student must be able to create a request");
        assertTrue((Boolean) response.getBody().get("success"),
                "Request creation response must have success=true");
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"), "Created request must have an id");
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
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Student must be able to list their own requests");
        assertTrue((Boolean) response.getBody().get("success"),
                "My requests response must have success=true");
        assertNotNull(response.getBody().get("data"),
                "My requests data must not be null");
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
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Teacher must be able to access pending approvals queue");
        assertTrue((Boolean) response.getBody().get("success"),
                "Pending approvals response must have success=true");
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
    void startPicking_asAdmin_isAccessible() {
        HttpClient admin = adminClient();
        HttpClient student = studentClient();

        // Create an item with requiresApproval=false + locationId=1.
        // Student's request will be AUTO_APPROVED immediately (pick task created).
        // Then admin can call start-picking to transition AUTO_APPROVED → PICKING.
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "StartPicking Item " + ts);
        itemReq.put("sku", "SP-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("locationId", 1);
        itemReq.put("priceUsd", 5.00);
        itemReq.put("quantity", 10);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode());
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Start picking endpoint test");
        ResponseEntity<Map> createResp = student.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(),
                "Request creation must succeed for start-picking test setup");
        Long requestId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Admin transitions AUTO_APPROVED → PICKING
        ResponseEntity<Map> response = admin.put(
                "/api/requests/" + requestId + "/start-picking", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to start picking an AUTO_APPROVED request");
        assertTrue((Boolean) response.getBody().get("success"),
                "start-picking response must have success=true");
    }

    @Test
    void startPicking_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put(
                "/api/requests/1/start-picking", null, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/requests/{id}/ready-for-pickup ──────────────────────

    @Test
    void readyForPickup_asAdmin_isAccessible() {
        HttpClient admin = adminClient();
        HttpClient student = studentClient();

        // Set up AUTO_APPROVED request, then advance to PICKING via start-picking,
        // then call ready-for-pickup to transition PICKING → READY_FOR_PICKUP.
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "ReadyForPickup Item " + ts);
        itemReq.put("sku", "RFP-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("locationId", 1);
        itemReq.put("priceUsd", 5.00);
        itemReq.put("quantity", 10);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode());
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Ready for pickup endpoint test");
        ResponseEntity<Map> createResp = student.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long requestId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // AUTO_APPROVED → PICKING
        ResponseEntity<Map> startPickingResp = admin.put(
                "/api/requests/" + requestId + "/start-picking", null, Map.class);
        assertEquals(HttpStatus.OK, startPickingResp.getStatusCode(),
                "start-picking setup step must succeed");

        // PICKING → READY_FOR_PICKUP
        ResponseEntity<Map> response = admin.put(
                "/api/requests/" + requestId + "/ready-for-pickup", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to mark a PICKING request as ready-for-pickup");
        assertTrue((Boolean) response.getBody().get("success"),
                "ready-for-pickup response must have success=true");
    }

    @Test
    void readyForPickup_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put(
                "/api/requests/1/ready-for-pickup", null, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
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
        HttpClient admin = adminClient();
        HttpClient student = studentClient();

        // Advance request through AUTO_APPROVED → PICKING → READY_FOR_PICKUP,
        // then call picked-up to complete the workflow.
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "PickedUp Item " + ts);
        itemReq.put("sku", "PU-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("locationId", 1);
        itemReq.put("priceUsd", 5.00);
        itemReq.put("quantity", 10);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode());
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Picked up endpoint test");
        ResponseEntity<Map> createResp = student.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long requestId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // AUTO_APPROVED → PICKING → READY_FOR_PICKUP → PICKED_UP
        admin.put("/api/requests/" + requestId + "/start-picking", null, Map.class);
        admin.put("/api/requests/" + requestId + "/ready-for-pickup", null, Map.class);

        ResponseEntity<Map> response = admin.put(
                "/api/requests/" + requestId + "/picked-up", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to mark a READY_FOR_PICKUP request as picked-up");
        assertTrue((Boolean) response.getBody().get("success"),
                "picked-up response must have success=true");
    }
}
