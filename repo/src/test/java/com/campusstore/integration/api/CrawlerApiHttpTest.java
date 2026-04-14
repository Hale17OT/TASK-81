package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/crawler/jobs ───────────────────────────────────

    @Test
    void listCrawlerJobs_asAdmin_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/crawler/jobs", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listCrawlerJobs_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/crawler/jobs", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listCrawlerJobs_asTeacher_returns403() {
        HttpClient client = teacherClient();
        ResponseEntity<Map> response = client.get("/api/admin/crawler/jobs", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listCrawlerJobs_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/crawler/jobs", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/admin/crawler/jobs ──────────────────────────────────

    @Test
    void createCrawlerJob_asAdmin_returns200() {
        HttpClient client = adminClient();

        Map<String, Object> req = Map.of(
                "name", "Test Crawler Job " + System.currentTimeMillis(),
                "sourcePath", "/data/test-source",
                "cronExpression", "0 0 2 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/crawler/jobs", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    @Test
    void createCrawlerJob_asStudent_returns403() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of(
                "name", "Forbidden Job",
                "sourcePath", "/data/test",
                "cronExpression", "0 0 2 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/crawler/jobs", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
