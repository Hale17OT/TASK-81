package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    // ── PUT /api/admin/crawler/jobs/{id} ─────────────────────────────

    @Test
    void updateCrawlerJob_asAdmin_isAccessible() {
        HttpClient client = adminClient();

        // First create a job to update
        Map<String, Object> createReq = Map.of(
                "name", "Update Job " + System.currentTimeMillis(),
                "sourcePath", "/data/update-source",
                "cronExpression", "0 0 3 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/crawler/jobs", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long jobId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> updateReq = Map.of(
                "name", "Updated Job " + System.currentTimeMillis(),
                "sourcePath", "/data/updated-source",
                "cronExpression", "0 0 4 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> response = client.put(
                "/api/admin/crawler/jobs/" + jobId, updateReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to update a crawler job");
        assertTrue((Boolean) response.getBody().get("success"),
                "Update crawler job response must have success=true");
    }

    @Test
    void updateCrawlerJob_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> updateReq = Map.of(
                "name", "Forbidden Update",
                "sourcePath", "/data/forbidden",
                "cronExpression", "0 0 4 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> response = client.put(
                "/api/admin/crawler/jobs/1", updateReq, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── POST /api/admin/crawler/jobs/{id}/run ────────────────────────

    @Test
    void runCrawlerJob_asAdmin_isAccessible() {
        HttpClient client = adminClient();

        // Create a job to run
        Map<String, Object> createReq = Map.of(
                "name", "Run Job " + System.currentTimeMillis(),
                "sourcePath", "/data/run-source",
                "cronExpression", "0 0 5 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/crawler/jobs", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long jobId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        ResponseEntity<Map> response = client.post(
                "/api/admin/crawler/jobs/" + jobId + "/run", Map.of(), Map.class);
        // runJob always returns 200: the service captures file/network failures as
        // CrawlerTaskStatus.FAILED records rather than propagating exceptions.
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must get 200 when running a crawler job (failures recorded as task entries)");
        assertTrue((Boolean) response.getBody().get("success"),
                "Run job response must have success=true");
    }

    @Test
    void runCrawlerJob_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.post(
                "/api/admin/crawler/jobs/1/run", Map.of(), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── GET /api/admin/crawler/jobs/{id}/failures ────────────────────

    @Test
    void getCrawlerJobFailures_asAdmin_isAccessible() {
        HttpClient client = adminClient();

        // Create a job to query failures for
        Map<String, Object> createReq = Map.of(
                "name", "Failures Job " + System.currentTimeMillis(),
                "sourcePath", "/data/failures-source",
                "cronExpression", "0 0 6 * * ?",
                "sourceType", "FILE"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/crawler/jobs", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long jobId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        ResponseEntity<Map> response = client.get(
                "/api/admin/crawler/jobs/" + jobId + "/failures", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to list crawler job failures");
        assertTrue((Boolean) response.getBody().get("success"),
                "Crawler job failures response must have success=true");
    }

    @Test
    void getCrawlerJobFailures_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get(
                "/api/admin/crawler/jobs/1/failures", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
