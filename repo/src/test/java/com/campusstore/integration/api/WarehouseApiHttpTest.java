package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/warehouse/locations ──────────────────────────────────

    @Test
    void listLocations_asAdmin_doesNotReturn401or403() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get(
                "/api/warehouse/locations", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to list warehouse locations");
        assertTrue((Boolean) response.getBody().get("success"),
                "Warehouse locations response must have success=true");
        assertNotNull(response.getBody().get("data"),
                "Warehouse locations data must not be null");
    }

    @Test
    void listLocations_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get(
                "/api/warehouse/locations", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listLocations_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/warehouse/locations", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/warehouse/locations ─────────────────────────────────

    @Test
    void createLocation_asAdmin_returns200() {
        HttpClient client = adminClient();

        // Get zone ID from listing
        ResponseEntity<Map> zonesResp = client.get("/api/admin/zones", Map.class);
        assertEquals(HttpStatus.OK, zonesResp.getStatusCode());
        List<Map<String, Object>> zones = (List<Map<String, Object>>) zonesResp.getBody().get("data");
        assertNotNull(zones);
        assertTrue(zones.size() > 0, "Expected at least one zone from test data");
        Long zoneId = ((Number) zones.get(0).get("id")).longValue();

        Map<String, Object> req = Map.of(
                "name", "LOC-NEW-" + System.currentTimeMillis(),
                "zoneId", zoneId,
                "x", 5.0,
                "y", 10.0,
                "level", 1,
                "temperatureZone", "AMBIENT",
                "securityLevel", "STANDARD",
                "capacity", 50
        );
        ResponseEntity<Map> response = client.post(
                "/api/warehouse/locations", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    // ── POST /api/warehouse/putaway ───────────────────────────────────

    @Test
    void recommendPutaway_asAdmin_doesNotReturn401or403() {
        HttpClient client = adminClient();

        // Use item id 1 from test data; quantity is validated @Min(1).
        // Test data includes zone 1 and location 1, so the putaway algorithm
        // should find at least one candidate location and return 200.
        Map<String, Object> req = Map.of("itemId", 1, "quantity", 1);
        ResponseEntity<Map> response = client.post(
                "/api/warehouse/putaway", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must get 200 from putaway recommendation with test data in place");
        assertTrue((Boolean) response.getBody().get("success"),
                "Putaway recommendation response must have success=true");
    }

    // ── POST /api/warehouse/pick-path ─────────────────────────────────

    @Test
    void generatePickPath_nonExistentTasks_returns200() {
        HttpClient client = adminClient();

        // Non-existent pick task IDs return empty path
        Map<String, Object> req = Map.of("pickTaskIds", List.of(999L));
        ResponseEntity<Map> response = client.post(
                "/api/warehouse/pick-path", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── POST /api/warehouse/simulate ──────────────────────────────────

    @Test
    void runSimulation_asAdmin_returns200() {
        HttpClient client = adminClient();

        Map<String, Object> req = Map.of("proposedLevelMultiplier", 2.0);
        ResponseEntity<Map> response = client.post(
                "/api/warehouse/simulate", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("orderCount"));
        assertNotNull(data.get("improvements"));
    }

    @Test
    void runSimulation_asStudent_returns403() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of("proposedLevelMultiplier", 2.0);
        ResponseEntity<Map> response = client.post(
                "/api/warehouse/simulate", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/warehouse/locations/{id} ────────────────────────────

    @Test
    void updateLocation_asAdmin_isAccessible() {
        HttpClient client = adminClient();

        // Get zone ID from listing
        ResponseEntity<Map> zonesResp = client.get("/api/admin/zones", Map.class);
        assertEquals(HttpStatus.OK, zonesResp.getStatusCode());
        List<Map<String, Object>> zones = (List<Map<String, Object>>) zonesResp.getBody().get("data");
        Long zoneId = ((Number) zones.get(0).get("id")).longValue();

        // Create a location to update
        Map<String, Object> createReq = Map.of(
                "name", "LOC-UPD-" + System.currentTimeMillis(),
                "zoneId", zoneId,
                "x", 3.0,
                "y", 4.0,
                "level", 1,
                "temperatureZone", "AMBIENT",
                "securityLevel", "STANDARD",
                "capacity", 30
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/warehouse/locations", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long locationId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Update the location
        Map<String, Object> updateReq = Map.of(
                "name", "LOC-UPDATED-" + System.currentTimeMillis(),
                "zoneId", zoneId,
                "x", 6.0,
                "y", 7.0,
                "level", 2,
                "temperatureZone", "AMBIENT",
                "securityLevel", "STANDARD",
                "capacity", 60
        );
        ResponseEntity<Map> response = client.put(
                "/api/warehouse/locations/" + locationId, updateReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to update a warehouse location");
        assertTrue((Boolean) response.getBody().get("success"),
                "Update location response must have success=true");
    }

    @Test
    void updateLocation_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> updateReq = Map.of(
                "name", "Forbidden Location",
                "zoneId", 1,
                "x", 1.0,
                "y", 1.0,
                "level", 1,
                "temperatureZone", "AMBIENT",
                "securityLevel", "STANDARD",
                "capacity", 10
        );
        ResponseEntity<Map> response = client.put(
                "/api/warehouse/locations/1", updateReq, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
