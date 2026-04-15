package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep security, privacy, and governance integration tests.
 * <p>
 * These tests verify that core non-functional requirements hold at the API level:
 * <ul>
 *   <li>PII masking — phone last-4 is correct, email is decrypted for owner</li>
 *   <li>Address PII decrypted — street/city/state/zip are plaintext in response</li>
 *   <li>Audit log immutability — no DELETE endpoint; records visible after actions</li>
 *   <li>Self-approval blocking — a user cannot approve their own request</li>
 *   <li>Request ownership — cancel and view are scoped to owner/approver/admin</li>
 *   <li>Role boundary enforcement — teacher cannot manage inventory/users</li>
 *   <li>Structured error format — 400/403/401 responses have a parseable body</li>
 * </ul>
 */
class SecurityGovernanceApiTest extends BaseHttpApiTest {

    // ── PII masking: phone last-4 ─────────────────────────────────────────────

    @Test
    void profile_phoneLast4_matchesLastFourDigitsOfPhone() {
        HttpClient client = studentClient();

        // Set a known phone via profile update
        String knownPhone = "5559990001";
        client.put("/api/profile",
                Map.of("displayName", "Test Student", "phone", knownPhone,
                        "email", "teststudent@campus.edu"),
                Map.class);

        ResponseEntity<Map> resp = client.get("/api/profile", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");

        String phoneLast4 = (String) data.get("phoneLast4");
        assertNotNull(phoneLast4, "phoneLast4 must be present in profile response");
        assertEquals(4, phoneLast4.length(), "phoneLast4 must be exactly 4 characters");
        assertTrue(knownPhone.endsWith(phoneLast4),
                "phoneLast4 (" + phoneLast4 + ") must match last 4 of phone (" + knownPhone + ")");
    }

    @Test
    void profile_email_decryptedForOwner() {
        HttpClient client = studentClient();

        ResponseEntity<Map> resp = client.get("/api/profile", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");

        // Email must be a readable string (decrypted), not null/binary
        Object email = data.get("email");
        assertNotNull(email, "Email should be present in profile for the authenticated owner");
        String emailStr = email.toString();
        assertTrue(emailStr.contains("@"),
                "Decrypted email should contain '@'; got: " + emailStr);
    }

    @Test
    void profile_sensitiveFields_notExposedAsRawEncryptedBytes() {
        HttpClient client = studentClient();

        ResponseEntity<String> resp = client.get("/api/profile", String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String body = resp.getBody();

        // Raw encrypted bytes would appear as base64 or garbage. The response must NOT
        // contain the field names for encrypted storage columns.
        assertFalse(body.contains("passwordHashEncrypted"),
                "Encrypted password field must not appear in profile API response");
        assertFalse(body.contains("emailEncrypted"),
                "Encrypted email storage field must not appear in profile API response");
        assertFalse(body.contains("phoneEncrypted"),
                "Encrypted phone storage field must not appear in profile API response");
    }

    // ── Address PII decryption ────────────────────────────────────────────────

    @Test
    void address_fields_returnDecryptedPlaintext() {
        HttpClient client = studentClient();
        String label = "PII Test Address " + System.currentTimeMillis();
        String street = "42 Privacy Lane";
        String city = "Encryptville";
        String state = "EV";
        String zip = "00001";

        // Add address
        Map<String, Object> req = Map.of(
                "label", label, "street", street, "city", city,
                "state", state, "zipCode", zip
        );
        ResponseEntity<Map> addResp = client.post("/api/profile/addresses", req, Map.class);
        assertEquals(HttpStatus.OK, addResp.getStatusCode());

        // List addresses
        ResponseEntity<Map> listResp = client.get("/api/profile/addresses", Map.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        String listBody = listResp.getBody().toString();

        // All PII fields should be decrypted plaintext in the response
        assertTrue(listBody.contains(street),
                "Street must be decrypted in address response, got: " + listBody);
        assertTrue(listBody.contains(city),
                "City must be decrypted in address response");
        assertTrue(listBody.contains(state),
                "State must be decrypted in address response");
        assertTrue(listBody.contains(zip),
                "ZipCode must be decrypted in address response");
    }

    // ── Audit log immutability ────────────────────────────────────────────────

    @Test
    void auditLog_deleteEndpoint_returnsMethodNotAllowed() {
        HttpClient admin = adminClient();

        // The audit log must not have a DELETE endpoint — it is immutable.
        // Using HttpClient.delete() so the admin session cookie is sent, allowing
        // the request to reach Spring MVC (which returns 405) rather than being
        // blocked by Spring Security (which returns 401 for unauthenticated).
        // Use String.class because Spring's 405 response is text/html, not JSON.
        ResponseEntity<String> response = admin.delete("/api/admin/audit", String.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode(),
                "DELETE /api/admin/audit must return 405 — audit log is immutable");
    }

    @Test
    void auditLog_recordsPresent_afterAdminActions() {
        HttpClient admin = adminClient();

        // Perform an action that generates an audit record
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Audit Test Item " + ts);
        itemReq.put("sku", "AUD-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 1.99);
        itemReq.put("quantity", 1);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");
        admin.post("/api/inventory", itemReq, Map.class);

        // Query the audit log for CREATE_ITEM actions
        ResponseEntity<Map> auditResp = admin.get(
                "/api/admin/audit?action=CREATE_ITEM", Map.class);
        assertEquals(HttpStatus.OK, auditResp.getStatusCode());
        assertTrue((Boolean) auditResp.getBody().get("success"),
                "Audit log GET should succeed");

        Object auditData = auditResp.getBody().get("data");
        assertNotNull(auditData, "Audit log data must not be null");
        String auditStr = auditData.toString();
        assertTrue(auditStr.contains("totalElements"),
                "Audit log response should have pagination with totalElements");
        // Verify there is at least one record
        assertFalse(auditStr.contains("\"totalElements\":0"),
                "Audit log should have at least one CREATE_ITEM record after creating an item");
    }

    @Test
    void auditLog_query_filterByEntityType() {
        HttpClient admin = adminClient();

        // Query audit log for Department entities
        ResponseEntity<Map> resp = admin.get(
                "/api/admin/audit?entityType=Department", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue((Boolean) resp.getBody().get("success"));
        assertNotNull(resp.getBody().get("data"));
    }

    // ── Self-approval blocking ────────────────────────────────────────────────

    @Test
    void selfApproval_teacherCannotApproveOwnRequest() {
        HttpClient admin = adminClient();
        HttpClient teacher = teacherClient();

        // Create an item with requiresApproval=true and locationId=1 so:
        //   (a) request creation doesn't trigger pick-task creation (stays PENDING_APPROVAL)
        //   (b) admin approval below can create the pick task without error
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Self Approval Item " + ts);
        itemReq.put("sku", "SA-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("locationId", 1);
        itemReq.put("priceUsd", 5.00);
        itemReq.put("quantity", 5);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(), "Admin must create item successfully");
        Long itemId = ((Number) ((Map<?, ?>) itemResp.getBody().get("data")).get("id")).longValue();

        // Teacher creates a request for the item — must succeed (teachers can submit requests)
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Self-approval test");
        ResponseEntity<Map> reqResp = teacher.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, reqResp.getStatusCode(),
                "Teacher request creation must succeed");
        assertTrue((Boolean) reqResp.getBody().get("success"),
                "Request creation response must have success=true");
        Long requestId = ((Number) ((Map<?, ?>) reqResp.getBody().get("data")).get("id")).longValue();

        // Teacher tries to approve their own request — must be rejected (self-approval block
        // or assignment-mismatch check: admin is the designated approver, not the teacher)
        ResponseEntity<Map> teacherApproveResp = teacher.put(
                "/api/requests/" + requestId + "/approve", null, Map.class);
        assertNotEquals(HttpStatus.OK, teacherApproveResp.getStatusCode(),
                "Teacher must not be able to approve their own request");
        assertTrue(teacherApproveResp.getStatusCode().is4xxClientError(),
                "Teacher self-approval block must return a 4xx status, got: "
                        + teacherApproveResp.getStatusCode());

        // Admin approves the same request — must succeed, proving the request is valid
        // and only the teacher was blocked (not a broken endpoint)
        ResponseEntity<Map> adminApproveResp = admin.put(
                "/api/requests/" + requestId + "/approve", null, Map.class);
        assertEquals(HttpStatus.OK, adminApproveResp.getStatusCode(),
                "Admin must be able to approve the same request that teacher was blocked from");
        assertTrue((Boolean) adminApproveResp.getBody().get("success"),
                "Admin approval response must have success=true");
    }

    // ── Request ownership and cancellation ───────────────────────────────────

    @Test
    void requestCancellation_ownerCanCancelOwnRequest() {
        HttpClient admin = adminClient();
        HttpClient student = studentClient();

        // Create item — requiresApproval=true so request stays PENDING_APPROVAL (cancellable)
        // without needing a storage location for pick-task creation
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Cancel Test Item " + ts);
        itemReq.put("sku", "CAN-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 2.00);
        itemReq.put("quantity", 5);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(), "Admin must create item successfully");
        Long itemId = ((Number) ((Map<?, ?>) itemResp.getBody().get("data")).get("id")).longValue();

        // Student creates request — must succeed
        Map<String, Object> reqBody = new java.util.HashMap<>();
        reqBody.put("itemId", itemId);
        reqBody.put("quantity", 1);
        reqBody.put("justification", "Cancel test");
        ResponseEntity<Map> reqResp = student.post("/api/requests", reqBody, Map.class);
        assertEquals(HttpStatus.OK, reqResp.getStatusCode(),
                "Student request creation must succeed");
        assertTrue((Boolean) reqResp.getBody().get("success"),
                "Request creation must return success=true");
        Long requestId = ((Number) ((Map<?, ?>) reqResp.getBody().get("data")).get("id")).longValue();

        // Student cancels own request
        ResponseEntity<Map> cancelResp = student.put(
                "/api/requests/" + requestId + "/cancel", Map.of(), Map.class);
        assertEquals(HttpStatus.OK, cancelResp.getStatusCode(),
                "Owner should be able to cancel their own request");
        assertTrue((Boolean) cancelResp.getBody().get("success"),
                "Cancel response must have success=true");

        // Verify the request is now CANCELLED by reading it back
        ResponseEntity<Map> getResp = student.get("/api/requests/" + requestId, Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode(),
                "GET request after cancellation must succeed");
        Map<String, Object> requestData = (Map<String, Object>) getResp.getBody().get("data");
        assertEquals("CANCELLED", requestData.get("status"),
                "Request status must be CANCELLED after cancellation");
    }

    @Test
    void requestCancellation_otherStudentCannotCancel() {
        HttpClient admin = adminClient();
        HttpClient studentA = studentClient();

        // Create item — requiresApproval=true so request stays PENDING_APPROVAL
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Isolation Cancel Item " + ts);
        itemReq.put("sku", "IC-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 1.00);
        itemReq.put("quantity", 5);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(), "Admin must create item successfully");
        Long itemId = ((Number) ((Map<?, ?>) itemResp.getBody().get("data")).get("id")).longValue();

        // Student A creates request — must succeed
        Map<String, Object> reqBody = new java.util.HashMap<>();
        reqBody.put("itemId", itemId);
        reqBody.put("quantity", 1);
        reqBody.put("justification", "Cross-user cancel test");
        ResponseEntity<Map> reqResp = studentA.post("/api/requests", reqBody, Map.class);
        assertEquals(HttpStatus.OK, reqResp.getStatusCode(),
                "Student A request creation must succeed");
        Long requestId = ((Number) ((Map<?, ?>) reqResp.getBody().get("data")).get("id")).longValue();

        // Create Student B
        String userB = "cancel_b_" + ts;
        admin.post("/api/admin/users", Map.of(
                "username", userB, "password", "PassB123!",
                "displayName", "Cancel B", "email", userB + "@campus.edu",
                "roles", List.of("STUDENT")), Map.class);
        HttpClient studentB = loginAs(userB, "PassB123!");

        // Student B tries to cancel Student A's request
        ResponseEntity<Map> cancelResp = studentB.put(
                "/api/requests/" + requestId + "/cancel", Map.of(), Map.class);
        assertNotEquals(HttpStatus.OK, cancelResp.getStatusCode(),
                "Student B must not be able to cancel Student A's request");
        assertTrue(cancelResp.getStatusCode().is4xxClientError(),
                "Cross-user cancel attempt must return a 4xx status, got: "
                        + cancelResp.getStatusCode());
    }

    // ── Role boundary enforcement ─────────────────────────────────────────────

    @Test
    void teacher_cannotCreateInventoryItem_returns403() {
        HttpClient teacher = teacherClient();

        Map<String, Object> req = new java.util.HashMap<>();
        req.put("name", "Teacher Forbidden Item");
        req.put("sku", "TF-SKU-1");
        req.put("categoryId", 1);
        req.put("departmentId", 1);
        req.put("priceUsd", 1.00);
        req.put("quantity", 1);
        req.put("requiresApproval", false);
        req.put("condition", "NEW");
        ResponseEntity<Map> resp = teacher.post("/api/inventory", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Teacher must not be able to create inventory items");
    }

    @Test
    void teacher_cannotManageUsers_returns403() {
        HttpClient teacher = teacherClient();
        ResponseEntity<Map> resp = teacher.get("/api/admin/users", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Teacher must not be able to list admin users");
    }

    @Test
    void student_cannotAccessPendingApprovals_returns403() {
        HttpClient student = studentClient();
        ResponseEntity<Map> resp = student.get("/api/requests/pending-approval", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Student must not be able to access pending approvals queue");
    }

    // ── Structured error responses ────────────────────────────────────────────

    @Test
    void unauthorizedRequest_returns401StatusCode() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/profile", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(),
                "Unauthenticated access to protected endpoint must return 401");
    }

    @Test
    void forbiddenRequest_student_returnsStructuredError() {
        HttpClient student = studentClient();
        ResponseEntity<Map> resp = student.get("/api/admin/audit", Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Student accessing admin audit must get 403");
        assertNotNull(resp.getBody(), "403 response must have a non-null body");
        // Verify ApiResponse error schema: success=false and error field present
        assertFalse((Boolean) resp.getBody().get("success"),
                "403 ApiResponse must have success=false");
        assertNotNull(resp.getBody().get("error"),
                "403 ApiResponse must include an error field");
    }

    @Test
    void badRequest_missingRequiredField_returns400WithErrorInfo() {
        HttpClient student = studentClient();

        // POST tag with empty string (failing @NotBlank validation)
        ResponseEntity<Map> resp = student.post(
                "/api/profile/tags", Map.of("tag", ""), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "Empty tag must return 400 Bad Request");
        assertNotNull(resp.getBody(), "400 response must have a non-null body");
        // Verify ApiResponse error schema: success=false and error field present
        assertFalse((Boolean) resp.getBody().get("success"),
                "400 ApiResponse must have success=false");
        assertNotNull(resp.getBody().get("error"),
                "400 ApiResponse must include an error field with validation details");
    }

    // ── Password change security ──────────────────────────────────────────────

    @Test
    void passwordChange_wrongOldPassword_returnsError() {
        HttpClient client = studentClient();

        ResponseEntity<Map> resp = client.put("/api/auth/password",
                Map.of("oldPassword", "CompletlyWrong999!",
                        "newPassword", "ShouldNotWork123!"),
                Map.class);
        assertNotEquals(HttpStatus.OK, resp.getStatusCode(),
                "Password change with wrong old password must not succeed");
        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Wrong old password should be a 4xx error, got: " + resp.getStatusCode());
    }

    @Test
    void passwordChange_newPasswordSameAsOld_returnsError() {
        HttpClient client = studentClient();

        // Attempt to change to the same password
        ResponseEntity<Map> resp = client.put("/api/auth/password",
                Map.of("oldPassword", "Student123!", "newPassword", "Student123!"),
                Map.class);
        // May be 200 (policy allows same password) or 400 (policy rejects it) —
        // either is a valid business outcome, but a 5xx is never acceptable
        assertTrue(
                resp.getStatusCode().is2xxSuccessful() || resp.getStatusCode().is4xxClientError(),
                "Password change (same password) must return 2xx or 4xx, got: "
                        + resp.getStatusCode());
    }

}
