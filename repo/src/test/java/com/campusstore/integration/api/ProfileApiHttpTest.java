package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileApiHttpTest extends BaseHttpApiTest {

    // ── GET /api/profile ──────────────────────────────────────────────

    @Test
    void getProfile_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("teststudent", data.get("username"));
    }

    @Test
    void getProfile_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/profile", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/profile ──────────────────────────────────────────────

    @Test
    void updateProfile_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> updateReq = Map.of(
                "displayName", "Updated Student Name",
                "email", "updatedstudent@campus.edu",
                "phone", "5559998888"
        );
        ResponseEntity<Map> response = client.put("/api/profile", updateReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── GET /api/profile/addresses ────────────────────────────────────

    @Test
    void listAddresses_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile/addresses", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── POST /api/profile/addresses ───────────────────────────────────

    @Test
    void addAddress_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, Object> addressReq = Map.of(
                "label", "Home",
                "street", "123 Test Street",
                "city", "Testville",
                "state", "TS",
                "zipCode", "12345"
        );
        ResponseEntity<Map> response = client.post(
                "/api/profile/addresses", addressReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
    }

    // ── GET /api/profile/tags ─────────────────────────────────────────

    @Test
    void listTags_authenticated_returns200() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile/tags", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── POST /api/profile/tags ────────────────────────────────────────

    @Test
    void addTag_validRequest_returns200() {
        HttpClient client = studentClient();

        Map<String, String> tagReq = Map.of("tag", "electronics");
        ResponseEntity<Map> response = client.post(
                "/api/profile/tags", tagReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── DELETE /api/profile/tags/{tag} ────────────────────────────────

    @Test
    void removeTag_existingTag_returns200() {
        HttpClient client = teacherClient();

        // First add a tag
        Map<String, String> tagReq = Map.of("tag", "books");
        client.post("/api/profile/tags", tagReq, Map.class);

        // Then delete it
        ResponseEntity<Map> response = client.delete(
                "/api/profile/tags/books", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    // ── GET /api/profile/contacts ────────────────────────────────────

    @Test
    void listContacts_authenticated_isAccessible() {
        HttpClient client = studentClient();
        ResponseEntity<Map> response = client.get("/api/profile/contacts", Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Student must be able to list their contacts");
        assertTrue((Boolean) response.getBody().get("success"),
                "Contacts list must have success=true");
    }

    // ── POST /api/profile/contacts ───────────────────────────────────

    @Test
    void addContact_authenticated_isAccessible() {
        HttpClient client = studentClient();
        Map<String, Object> contactReq = Map.of(
                "type", "EMAIL",
                "value", "contact_test@campus.edu",
                "label", "Personal"
        );
        ResponseEntity<Map> response = client.post(
                "/api/profile/contacts", contactReq, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Student must be able to add a contact");
        assertTrue((Boolean) response.getBody().get("success"),
                "Add contact response must have success=true");
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"), "New contact must have an id");
    }

    // ── PUT /api/profile/contacts/{id} ───────────────────────────────

    @Test
    void updateContact_authenticated_isAccessible() {
        HttpClient client = studentClient();

        // Create a contact first to get a valid id
        Map<String, Object> createReq = Map.of(
                "type", "EMAIL",
                "value", "update_before@campus.edu",
                "label", "Before Update"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/profile/contacts", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(),
                "Contact creation must succeed before update test");
        Long contactId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Update the created contact
        Map<String, Object> updateReq = Map.of(
                "type", "EMAIL",
                "value", "updated_contact@campus.edu",
                "label", "Updated"
        );
        ResponseEntity<Map> updateResp = client.put(
                "/api/profile/contacts/" + contactId, updateReq, Map.class);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode(),
                "Authenticated user must be able to update their own contact");
        assertTrue((Boolean) updateResp.getBody().get("success"),
                "Update contact response must have success=true");
    }

    @Test
    void updateContact_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/profile/contacts/1",
                org.springframework.http.HttpMethod.PUT,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── DELETE /api/profile/contacts/{id} ────────────────────────────

    @Test
    void deleteContact_authenticated_isAccessible() {
        HttpClient client = studentClient();

        // Create a contact first to get a valid id
        Map<String, Object> createReq = Map.of(
                "type", "PHONE",
                "value", "5550001234",
                "label", "To Delete"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/profile/contacts", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(),
                "Contact creation must succeed before delete test");
        Long contactId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Delete the created contact
        ResponseEntity<Map> deleteResp = client.delete(
                "/api/profile/contacts/" + contactId, Map.class);
        assertEquals(HttpStatus.OK, deleteResp.getStatusCode(),
                "Authenticated user must be able to delete their own contact");
        assertTrue((Boolean) deleteResp.getBody().get("success"),
                "Delete contact response must have success=true");
    }

    @Test
    void deleteContact_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/profile/contacts/1",
                org.springframework.http.HttpMethod.DELETE,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/profile (unauth) ────────────────────────────────────

    @Test
    void updateProfile_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/profile",
                org.springframework.http.HttpMethod.PUT,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── PUT /api/profile/addresses/{id} ──────────────────────────────

    @Test
    void updateAddress_authenticated_isAccessible() {
        HttpClient client = studentClient();

        // Create an address first to get a valid id
        Map<String, Object> createReq = Map.of(
                "label", "Before Update Address",
                "street", "100 Before Street",
                "city", "Beforecity",
                "state", "BC",
                "zipCode", "10000"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/profile/addresses", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(),
                "Address creation must succeed before update test");
        Long addressId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Update the created address
        Map<String, Object> updateReq = Map.of(
                "label", "Updated Home",
                "street", "456 Updated Street",
                "city", "Updateville",
                "state", "UP",
                "zipCode", "54321"
        );
        ResponseEntity<Map> updateResp = client.put(
                "/api/profile/addresses/" + addressId, updateReq, Map.class);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode(),
                "Authenticated user must be able to update their own address");
        assertTrue((Boolean) updateResp.getBody().get("success"),
                "Update address response must have success=true");
    }

    @Test
    void updateAddress_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/profile/addresses/1",
                org.springframework.http.HttpMethod.PUT,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ── DELETE /api/profile/addresses/{id} ──────────────────────────

    @Test
    void deleteAddress_authenticated_isAccessible() {
        HttpClient client = studentClient();

        // Create an address first to get a valid id
        Map<String, Object> createReq = Map.of(
                "label", "To Delete Address",
                "street", "999 Delete Road",
                "city", "Deletecity",
                "state", "DL",
                "zipCode", "99999"
        );
        ResponseEntity<Map> createResp = client.post(
                "/api/profile/addresses", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(),
                "Address creation must succeed before delete test");
        Long addressId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // Delete the created address
        ResponseEntity<Map> deleteResp = client.delete(
                "/api/profile/addresses/" + addressId, Map.class);
        assertEquals(HttpStatus.OK, deleteResp.getStatusCode(),
                "Authenticated user must be able to delete their own address");
        assertTrue((Boolean) deleteResp.getBody().get("success"),
                "Delete address response must have success=true");
    }

    @Test
    void deleteAddress_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/profile/addresses/1",
                org.springframework.http.HttpMethod.DELETE,
                null,
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
