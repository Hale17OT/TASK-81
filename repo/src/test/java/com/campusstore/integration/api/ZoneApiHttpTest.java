package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/zones ──────────────────────────────────────────

    @Test
    void listZones_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/zones", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listZones_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/zones", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listZones_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/zones", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/admin/zones ─────────────────────────────────────────

    @Test
    void createZone_asAdmin_returns200() {
        HttpClient client = adminClient();

        Map<String, Object> req = Map.of(
                "name", "Zone " + System.currentTimeMillis(),
                "description", "A zone created by test",
                "building", "Building A",
                "floorLevel", 2
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/zones", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    // ── POST /api/admin/zones/distances ───────────────────────────────

    @Test
    void setZoneDistance_asAdmin_returns200() {
        HttpClient client = adminClient();

        // Create two zones first
        Map<String, Object> zone1Req = Map.of(
                "name", "ZoneA_" + System.currentTimeMillis(),
                "description", "Zone A",
                "building", "B1",
                "floorLevel", 1
        );
        ResponseEntity<Map> zone1Resp = client.post(
                "/api/admin/zones", zone1Req, Map.class);
        assertEquals(HttpStatus.OK, zone1Resp.getStatusCode());
        Long zone1Id = ((Number) ((Map<String, Object>) zone1Resp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> zone2Req = Map.of(
                "name", "ZoneB_" + System.currentTimeMillis(),
                "description", "Zone B",
                "building", "B2",
                "floorLevel", 2
        );
        ResponseEntity<Map> zone2Resp = client.post(
                "/api/admin/zones", zone2Req, Map.class);
        assertEquals(HttpStatus.OK, zone2Resp.getStatusCode());
        Long zone2Id = ((Number) ((Map<String, Object>) zone2Resp.getBody().get("data")).get("id")).longValue();

        // Set distance between zones
        Map<String, Object> distReq = Map.of(
                "fromZoneId", zone1Id,
                "toZoneId", zone2Id,
                "weight", 5.0
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/zones/distances", distReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }
}
