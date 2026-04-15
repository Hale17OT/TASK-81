package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAdminApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/admin/users ──────────────────────────────────────────

    @Test
    void listUsers_asAdmin_doesNotReturn401or403() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/admin/users", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to list users");
        assertTrue((Boolean) response.getBody().get("success"),
                "User list response must have success=true");
        assertNotNull(response.getBody().get("data"),
                "User list data must not be null");
    }

    @Test
    void listUsers_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/users", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void listUsers_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/admin/users", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── POST /api/admin/users ─────────────────────────────────────────

    @Test
    void createUser_asAdmin_returns200() {
        HttpClient client = adminClient();

        Map<String, Object> req = Map.of(
                "username", "newuser_" + System.currentTimeMillis(),
                "password", "SecurePass123!",
                "displayName", "New User",
                "email", "newuser" + System.currentTimeMillis() + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/users", req, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    @Test
    void createUser_asStudent_returns403() {
        HttpClient client = studentClient();

        Map<String, Object> req = Map.of(
                "username", "forbidden_user",
                "password", "SecurePass123!",
                "displayName", "Forbidden User",
                "email", "forbidden@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> response = client.post(
                "/api/admin/users", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── GET /api/admin/users/{id} ─────────────────────────────────────

    @Test
    void getUser_asAdmin_doesNotReturn401or403() {
        HttpClient client = adminClient();

        // Create a user to retrieve
        String ts = String.valueOf(System.currentTimeMillis());
        String username = "getuser_" + ts;
        Map<String, Object> createReq = Map.of(
                "username", username,
                "password", "SecurePass123!",
                "displayName", "Get User Test",
                "email", "getuser" + ts + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/users", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long userId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Retrieve the created user — must return 200 with success=true
        ResponseEntity<Map> response = client.get(
                "/api/admin/users/" + userId, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to get a user by id");
        assertTrue((Boolean) response.getBody().get("success"),
                "Get user response must have success=true");
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(username, data.get("username"),
                "Retrieved user must have the expected username");
    }

    // ── PUT /api/admin/users/{id} ─────────────────────────────────────

    @Test
    void updateUser_asAdmin_returns200() {
        HttpClient client = adminClient();

        // Create a user to update
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> createReq = Map.of(
                "username", "updateuser_" + ts,
                "password", "SecurePass123!",
                "displayName", "Update User Test",
                "email", "updateuser" + ts + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/users", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long userId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Update the user
        Map<String, Object> updateReq = Map.of(
                "displayName", "Updated Display Name",
                "email", "updated" + ts + "@campus.edu"
        );
        ResponseEntity<Map> response = client.put(
                "/api/admin/users/" + userId, updateReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── GET /api/admin/users/{id} (student → 403) ───────────────────

    @Test
    void getUser_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/admin/users/1", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/admin/users/{id} (student → 403) ───────────────────

    @Test
    void updateUser_asStudent_returns403() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.put("/api/admin/users/1",
                Map.of("displayName", "Forbidden"), Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/admin/users/{id}/status ─────────────────────────────

    @Test
    void updateUserStatus_asAdmin_isAccessible() {
        HttpClient client = adminClient();

        // Create a user to update status
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> createReq = Map.of(
                "username", "statususer_" + ts,
                "password", "SecurePass123!",
                "displayName", "Status User Test",
                "email", "statususer" + ts + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/users", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long userId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> statusReq = Map.of("status", "DISABLED");
        ResponseEntity<Map> response = client.put(
                "/api/admin/users/" + userId + "/status", statusReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to update user status");
        assertTrue((Boolean) response.getBody().get("success"),
                "Update status response must have success=true");
    }

    @Test
    void updateUserStatus_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> statusReq = Map.of("active", false);
        ResponseEntity<Map> response = client.put(
                "/api/admin/users/1/status", statusReq, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // ── PUT /api/admin/users/{id}/roles ──────────────────────────────

    @Test
    void updateUserRoles_asAdmin_isAccessible() {
        HttpClient client = adminClient();

        // Create a user to update roles
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> createReq = Map.of(
                "username", "rolesuser_" + ts,
                "password", "SecurePass123!",
                "displayName", "Roles User Test",
                "email", "rolesuser" + ts + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/admin/users", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        Long userId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        Map<String, Object> rolesReq = Map.of("roles", List.of("STUDENT", "TEACHER"));
        ResponseEntity<Map> response = client.put(
                "/api/admin/users/" + userId + "/roles", rolesReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Admin must be able to update user roles");
        assertTrue((Boolean) response.getBody().get("success"),
                "Update roles response must have success=true");
    }

    @Test
    void updateUserRoles_asStudent_returns403() {
        HttpClient client = studentClient();
        Map<String, Object> rolesReq = Map.of("roles", List.of("ADMIN"));
        ResponseEntity<Map> response = client.put(
                "/api/admin/users/1/roles", rolesReq, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
