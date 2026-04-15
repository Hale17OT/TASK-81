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

class AuthApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/auth/me ──────────────────────────────────────────────

    @Test
    void me_authenticated_returns200() {
        HttpClient client = adminClient();
        ResponseEntity<Map> response = client.get("/api/auth/me", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data);
        assertEquals("testadmin", data.get("username"));
    }

    @Test
    void me_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/me", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/auth/password ────────────────────────────────────────

    @Test
    void changePassword_validRequest_returns200() {
        // Create a temporary user via the admin API to avoid side effects
        HttpClient admin = adminClient();

        Map<String, Object> createReq = Map.of(
                "username", "pwdchangeuser",
                "password", "OldPass123!",
                "displayName", "PwdChange User",
                "email", "pwdchange@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> createResp = admin.post("/api/admin/users", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());

        // Login as the new user
        HttpClient userClient = loginAs("pwdchangeuser", "OldPass123!");

        // Change password
        Map<String, String> changeReq = Map.of(
                "oldPassword", "OldPass123!",
                "newPassword", "NewPass456!"
        );
        ResponseEntity<Map> changeResp = userClient.put("/api/auth/password", changeReq, Map.class);
        assertEquals(HttpStatus.OK, changeResp.getStatusCode());
        assertTrue((Boolean) changeResp.getBody().get("success"));

        // Verify new password works by logging in again
        HttpClient newLogin = loginAs("pwdchangeuser", "NewPass456!");
        ResponseEntity<Map> meResp = newLogin.get("/api/auth/me", Map.class);
        assertEquals(HttpStatus.OK, meResp.getStatusCode());
    }

    @Test
    void changePassword_wrongOldPassword_returns400() {
        HttpClient client = studentClient();

        Map<String, String> changeReq = Map.of(
                "oldPassword", "WrongPassword!",
                "newPassword", "NewPass456!"
        );
        ResponseEntity<Map> response = client.put("/api/auth/password", changeReq, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ── POST /api/auth/login ─────────────────────────────────────────

    @Test
    void login_validCredentials_isAccessible() {
        // Use restTemplate directly to POST login (no prior session needed)
        Map<String, String> loginReq = Map.of(
                "username", "teststudent",
                "password", "Student123!"
        );
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Map<String, String>> entity =
                new org.springframework.http.HttpEntity<>(loginReq, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", entity, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Valid credentials must return 200 OK");
        assertTrue((Boolean) response.getBody().get("success"),
                "Login response must have success=true");
    }

    @Test
    void login_wrongCredentials_returnsError() {
        Map<String, String> loginReq = Map.of(
                "username", "teststudent",
                "password", "WrongPassword!"
        );
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Map<String, String>> entity =
                new org.springframework.http.HttpEntity<>(loginReq, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", entity, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Wrong credentials must return 401 Unauthorized");
    }

    // ── POST /api/auth/logout ────────────────────────────────────────

    @Test
    void logout_authenticated_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.post(
                "/api/auth/logout", null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Authenticated logout must return 200 OK");
        assertTrue((Boolean) response.getBody().get("success"),
                "Logout response must have success=true");
    }

    @Test
    void logout_unauthenticated_returns401() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<?> entity =
                new org.springframework.http.HttpEntity<>(null, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/logout", entity, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
