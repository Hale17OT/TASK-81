package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/inventory ───────────────────────────────────────────
    @Test
    void listItems_asAdmin_isAccessible() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/inventory", Map.class);
        // Should not be 401/403
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void listItems_asStudent_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/inventory", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void listItems_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/inventory", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── GET /api/inventory/{id} ──────────────────────────────────────
    @Test
    void getItem_asAdmin_isAccessible() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/inventory/1", Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void getItem_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/inventory/1", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/inventory (admin only) ─────────────────────────────
    @Test
    void createItem_asAdmin_isAccessible() {
        HttpClient client = adminClient();
        Map<String, Object> req = new HashMap<>();
        req.put("name", "Integration Test Item " + System.currentTimeMillis());
        req.put("description", "Created by test");
        req.put("sku", "SKU-IT-" + System.currentTimeMillis());
        req.put("categoryId", 1);
        req.put("departmentId", 1);
        req.put("priceUsd", 19.99);
        req.put("quantity", 10);
        req.put("requiresApproval", false);
        req.put("condition", "NEW");

        ResponseEntity<Map> response = client.post("/api/inventory", req, Map.class);
        assertNotEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
        assertNotEquals(HttpStatus.FORBIDDEN.value(), response.getStatusCode().value());
    }

    @Test
    void createItem_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> req = new HashMap<>();
        req.put("name", "Forbidden Item");
        req.put("sku", "SKU-FORBIDDEN");
        req.put("categoryId", 1);
        req.put("priceUsd", 5.00);
        req.put("quantity", 1);
        req.put("condition", "NEW");

        ResponseEntity<Map> response = client.post("/api/inventory", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── DELETE /api/inventory/{id} (admin only) ──────────────────────
    @Test
    void deleteItem_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.delete("/api/inventory/1", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
