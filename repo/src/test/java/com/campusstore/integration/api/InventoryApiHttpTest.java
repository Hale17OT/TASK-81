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
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Admin must be able to list inventory items");
        assertTrue((Boolean) response.getBody().get("success"), "Inventory list must have success=true");
        assertNotNull(response.getBody().get("data"), "Inventory list data must not be null");
    }

    @Test
    void listItems_asStudent_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/inventory", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Student must be able to list inventory items");
        assertTrue((Boolean) response.getBody().get("success"), "Inventory list must have success=true");
        assertNotNull(response.getBody().get("data"), "Inventory list data must not be null");
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
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Admin must be able to get inventory item by id");
        assertTrue((Boolean) response.getBody().get("success"), "Item response must have success=true");
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data, "Item data must not be null");
        assertNotNull(data.get("name"), "Item data must include the name field");
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
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Admin must be able to create inventory items");
        assertTrue((Boolean) response.getBody().get("success"), "Create response must have success=true");
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data, "Create response data must not be null");
        assertNotNull(data.get("id"), "Created item must have an id");
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

    // ── PUT /api/inventory/{id} (admin only) ───────────────────────
    @Test
    void updateItem_asAdmin_isAccessible() {
        HttpClient client = adminClient();
        Map<String, Object> req = new HashMap<>();
        req.put("name", "Updated Item " + System.currentTimeMillis());
        req.put("description", "Updated by test");
        req.put("priceUsd", 29.99);
        req.put("quantity", 20);

        ResponseEntity<Map> response = client.put("/api/inventory/1", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Admin must be able to update inventory items");
        assertTrue((Boolean) response.getBody().get("success"), "Update response must have success=true");
    }

    @Test
    void updateItem_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> req = new HashMap<>();
        req.put("name", "Forbidden Update");

        ResponseEntity<Map> response = client.put("/api/inventory/1", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── DELETE /api/inventory/{id} (admin only) ──────────────────────
    @Test
    void deleteItem_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.delete("/api/inventory/1", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void deleteItem_asAdmin_deactivatesItem() {
        HttpClient client = adminClient();

        // Create a fresh item to delete so we don't affect seeded data used by other tests
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> req = new HashMap<>();
        req.put("name", "Delete Test Item " + ts);
        req.put("description", "Created for deletion test");
        req.put("sku", "DEL-SKU-" + ts);
        req.put("categoryId", 1);
        req.put("departmentId", 1);
        req.put("priceUsd", 1.00);
        req.put("quantity", 1);
        req.put("requiresApproval", false);
        req.put("condition", "NEW");
        ResponseEntity<Map> createResp = client.post("/api/inventory", req, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long itemId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Admin deletes the item
        ResponseEntity<Map> deleteResp = client.delete("/api/inventory/" + itemId, Map.class);
        assertEquals(HttpStatus.OK, deleteResp.getStatusCode(),
                "Admin must be able to delete an inventory item");
        assertTrue((Boolean) deleteResp.getBody().get("success"),
                "Delete response must have success=true");

        // Verify deactivation: service throws ResourceNotFoundException for inactive items → 404
        ResponseEntity<Map> getResp = client.get("/api/inventory/" + itemId, Map.class);
        assertEquals(HttpStatus.NOT_FOUND, getResp.getStatusCode(),
                "Deleted (deactivated) item must return 404 — inactive items are not accessible");
    }
}
