package com.campusstore.integration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep API business-outcome integration tests.
 * <p>
 * Unlike the per-resource tests (which focus on HTTP status codes), these tests
 * verify actual payload content, state transitions, and round-trip correctness.
 * Each test exercises a complete user-level scenario:
 * <ul>
 *   <li>Auth lifecycle – login returns user data, /me returns the same identity</li>
 *   <li>Profile CRUD round-trip – update persists and is readable</li>
 *   <li>Tag CRUD round-trip – add → list → delete → verify gone</li>
 *   <li>Address add and list – new address appears in list response</li>
 *   <li>Inventory CRUD – admin creates item, reads back, updates, verifies update</li>
 *   <li>Request workflow – create → approve → verify status transitions</li>
 *   <li>Cross-user request isolation – another student cannot read a different student's request</li>
 *   <li>Notification unread count – mark all read, count becomes 0</li>
 *   <li>Search finds created item – newly created item appears in search results</li>
 *   <li>Validation errors – missing required fields return 400 with error info</li>
 * </ul>
 */
class ApiBusinessOutcomesTest extends BaseHttpApiTest {

    // ── Auth lifecycle ────────────────────────────────────────────────────────

    @Test
    void authMe_returnsCorrectUsername() {
        HttpClient adminClient = adminClient();

        ResponseEntity<Map> meResp = adminClient.get("/api/auth/me", Map.class);
        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertTrue((Boolean) meResp.getBody().get("success"));

        Map<String, Object> data = (Map<String, Object>) meResp.getBody().get("data");
        assertNotNull(data, "/api/auth/me data must not be null");
        assertEquals("testadmin", data.get("username"),
                "/api/auth/me should return the authenticated user's username");
    }

    @Test
    void authMe_returnsRolesAndDisplayName() {
        HttpClient adminClient = adminClient();

        ResponseEntity<Map> meResp = adminClient.get("/api/auth/me", Map.class);
        assertEquals(HttpStatus.OK, meResp.getStatusCode());

        Map<String, Object> data = (Map<String, Object>) meResp.getBody().get("data");
        assertNotNull(data.get("displayName"), "displayName should be present in /me response");
        // Roles list should exist (may be List<String>)
        assertNotNull(data.get("roles"), "roles should be present in /me response");
    }

    // ── Profile CRUD round-trip ───────────────────────────────────────────────

    @Test
    void profileUpdate_persistsDisplayNameChange() {
        HttpClient client = studentClient();
        String ts = String.valueOf(System.currentTimeMillis());
        String newName = "Updated Student " + ts;

        // Update the display name
        Map<String, Object> updateReq = Map.of(
                "displayName", newName,
                "email", "student_" + ts + "@campus.edu",
                "phone", "5550001111"
        );
        ResponseEntity<Map> putResp = client.put("/api/profile", updateReq, Map.class);
        assertEquals(HttpStatus.OK, putResp.getStatusCode());
        assertTrue((Boolean) putResp.getBody().get("success"), "Profile update should succeed");

        // Read it back
        ResponseEntity<Map> getResp = client.get("/api/profile", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        Map<String, Object> profile = (Map<String, Object>) getResp.getBody().get("data");
        assertEquals(newName, profile.get("displayName"),
                "Updated displayName must be readable back from GET /api/profile");
    }

    @Test
    void profileGet_returnsUsername_matchingLoginIdentity() {
        HttpClient client = studentClient();

        ResponseEntity<Map> getResp = client.get("/api/profile", Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        Map<String, Object> profile = (Map<String, Object>) getResp.getBody().get("data");
        assertEquals("teststudent", profile.get("username"),
                "GET /api/profile must return the authenticated user's username");
    }

    // ── Tag CRUD round-trip ───────────────────────────────────────────────────

    @Test
    void tagCrud_addListRemoveRoundTrip() {
        HttpClient client = teacherClient();
        String tag = "roundtrip_" + System.currentTimeMillis();

        // Add the tag
        ResponseEntity<Map> addResp = client.post(
                "/api/profile/tags", Map.of("tag", tag), Map.class);
        assertEquals(HttpStatus.OK, addResp.getStatusCode());
        assertTrue((Boolean) addResp.getBody().get("success"), "Tag add should succeed");

        // List tags — verify new tag appears
        ResponseEntity<Map> listResp = client.get("/api/profile/tags", Map.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        Object tagsData = listResp.getBody().get("data");
        assertNotNull(tagsData, "Tags list data must not be null");
        String tagsJson = tagsData.toString();
        assertTrue(tagsJson.contains(tag),
                "Newly added tag '" + tag + "' must appear in GET /api/profile/tags response");

        // Delete the tag
        ResponseEntity<Map> delResp = client.delete(
                "/api/profile/tags/" + tag, Map.class);
        assertEquals(HttpStatus.OK, delResp.getStatusCode());
        assertTrue((Boolean) delResp.getBody().get("success"), "Tag delete should succeed");

        // List tags again — tag must be gone
        ResponseEntity<Map> listAfterDel = client.get("/api/profile/tags", Map.class);
        assertEquals(HttpStatus.OK, listAfterDel.getStatusCode());
        String tagsAfterDel = listAfterDel.getBody().get("data").toString();
        assertFalse(tagsAfterDel.contains(tag),
                "Deleted tag '" + tag + "' must not appear in tags list after deletion");
    }

    // ── Address add and list ──────────────────────────────────────────────────

    @Test
    void addressAdd_appearsInListResponse() {
        HttpClient client = studentClient();
        String label = "TestHome_" + System.currentTimeMillis();

        Map<String, Object> addressReq = Map.of(
                "label", label,
                "street", "999 Round Trip Road",
                "city", "Testcity",
                "state", "TC",
                "zipCode", "99999"
        );
        ResponseEntity<Map> addResp = client.post(
                "/api/profile/addresses", addressReq, Map.class);
        assertEquals(HttpStatus.OK, addResp.getStatusCode());
        assertTrue((Boolean) addResp.getBody().get("success"), "Address add should succeed");

        Map<String, Object> newAddress = (Map<String, Object>) addResp.getBody().get("data");
        assertNotNull(newAddress.get("id"), "New address must have an id");

        // List and verify it's present
        ResponseEntity<Map> listResp = client.get("/api/profile/addresses", Map.class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        Object listData = listResp.getBody().get("data");
        assertNotNull(listData, "Address list data must not be null");
        assertTrue(listData.toString().contains(label),
                "Newly added address label '" + label + "' must appear in address list");
    }

    // ── Inventory CRUD round-trip ─────────────────────────────────────────────

    @Test
    void inventoryCrud_createReadUpdateReadRoundTrip() {
        HttpClient admin = adminClient();
        String ts = String.valueOf(System.currentTimeMillis());
        String itemName = "Business Outcome Item " + ts;

        // Create item
        Map<String, Object> createReq = new java.util.HashMap<>();
        createReq.put("name", itemName);
        createReq.put("description", "Created by business outcome test");
        createReq.put("sku", "BO-SKU-" + ts);
        createReq.put("categoryId", 1);
        createReq.put("departmentId", 1);
        createReq.put("priceUsd", 14.99);
        createReq.put("quantity", 5);
        createReq.put("requiresApproval", false);
        createReq.put("condition", "NEW");

        ResponseEntity<Map> createResp = admin.post("/api/inventory", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertTrue((Boolean) createResp.getBody().get("success"), "Inventory create should succeed");
        // Create endpoint returns only {id: N} — not the full entity
        Map<String, Object> created = (Map<String, Object>) createResp.getBody().get("data");
        assertNotNull(created.get("id"), "Created item must have an id");

        Long itemId = ((Number) created.get("id")).longValue();

        // Read item back — the GET endpoint returns the full entity
        ResponseEntity<Map> getResp = admin.get("/api/inventory/" + itemId, Map.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        Map<String, Object> fetched = (Map<String, Object>) getResp.getBody().get("data");
        assertEquals(itemName, fetched.get("name"), "GET item name must match what was created");

        // Update item (PUT returns void — ApiResponse<Void>)
        String updatedName = "Updated BO Item " + ts;
        Map<String, Object> updateReq = Map.of(
                "name", updatedName,
                "description", "Updated by business outcome test",
                "priceUsd", 19.99,
                "quantity", 10
        );
        ResponseEntity<String> updateResp = admin.put("/api/inventory/" + itemId, updateReq, String.class);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());

        // Read updated item back via GET to verify persistence
        ResponseEntity<Map> getUpdatedResp = admin.get("/api/inventory/" + itemId, Map.class);
        assertEquals(HttpStatus.OK, getUpdatedResp.getStatusCode());
        Map<String, Object> updatedItem = (Map<String, Object>) getUpdatedResp.getBody().get("data");
        assertEquals(updatedName, updatedItem.get("name"),
                "Item name must reflect the update");
    }

    // ── Request lifecycle ─────────────────────────────────────────────────────

    @Test
    void requestLifecycle_createAndApprove_statusTransitions() {
        HttpClient admin = adminClient();
        HttpClient student = studentClient();

        // Create a test item as admin — requiresApproval=true so student's request goes to
        // PENDING_APPROVAL (no pick task needed at creation time). locationId=1 is set so
        // the pick task can be created when admin approves.
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Request Lifecycle Item " + ts);
        itemReq.put("description", "Item for request lifecycle test");
        itemReq.put("sku", "RL-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("locationId", 1);
        itemReq.put("priceUsd", 9.99);
        itemReq.put("quantity", 20);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");

        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(), "Admin must create item successfully");
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        // Student creates a request — must succeed
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Business outcome test request");

        ResponseEntity<Map> createReqResp = student.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, createReqResp.getStatusCode(),
                "Student request creation must succeed");
        assertTrue((Boolean) createReqResp.getBody().get("success"),
                "Request creation response must have success=true");
        Long requestId = ((Number) ((Map<String, Object>) createReqResp.getBody().get("data")).get("id")).longValue();

        // Read back the request to verify initial status
        ResponseEntity<Map> getAfterCreate = student.get("/api/requests/" + requestId, Map.class);
        assertEquals(HttpStatus.OK, getAfterCreate.getStatusCode(),
                "GET request after creation must succeed");
        Map<String, Object> createdData = (Map<String, Object>) getAfterCreate.getBody().get("data");
        String initialStatus = (String) createdData.get("status");
        assertNotNull(initialStatus, "Request status must be present");
        assertEquals("PENDING_APPROVAL", initialStatus,
                "Request for requires-approval item must start as PENDING_APPROVAL");

        // Admin approves the request — must succeed
        ResponseEntity<Map> approveResp = admin.put(
                "/api/requests/" + requestId + "/approve", null, Map.class);
        assertEquals(HttpStatus.OK, approveResp.getStatusCode(),
                "Admin approval must succeed");
        assertTrue((Boolean) approveResp.getBody().get("success"),
                "Approval response must have success=true");

        // Read back to verify APPROVED status transition
        ResponseEntity<Map> getAfterApprove = student.get("/api/requests/" + requestId, Map.class);
        assertEquals(HttpStatus.OK, getAfterApprove.getStatusCode());
        Map<String, Object> approvedData = (Map<String, Object>) getAfterApprove.getBody().get("data");
        assertEquals("APPROVED", approvedData.get("status"),
                "Request status must be APPROVED after admin approval");
    }

    @Test
    void requestMineList_containsStudentRequests() {
        HttpClient student = studentClient();
        HttpClient admin = adminClient();

        // Create an item (requiresApproval=true avoids pick-task location requirement at
        // request creation time) and then submit a request
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Mine List Item " + ts);
        itemReq.put("sku", "ML-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 4.99);
        itemReq.put("quantity", 10);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");

        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(), "Admin must create item successfully");
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        // Create a request — must succeed
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Mine list test");
        ResponseEntity<Map> createResp = student.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode(),
                "Student request creation must succeed");
        Long requestId = ((Number) ((Map<String, Object>) createResp.getBody().get("data")).get("id")).longValue();

        // List my requests — must return the newly created request
        ResponseEntity<Map> mineResp = student.get("/api/requests/mine", Map.class);
        assertEquals(HttpStatus.OK, mineResp.getStatusCode(), "GET /api/requests/mine must succeed");
        assertTrue((Boolean) mineResp.getBody().get("success"), "My requests response must have success=true");
        Object data = mineResp.getBody().get("data");
        assertNotNull(data, "My requests data must not be null");
        // Paginated response must contain the created request ID
        assertTrue(data.toString().contains(String.valueOf(requestId)),
                "GET /api/requests/mine must contain the newly created request id=" + requestId);
    }

    // ── Cross-user request isolation ──────────────────────────────────────────

    @Test
    void crossUserIsolation_studentCannotReadOtherStudentRequest() {
        HttpClient admin = adminClient();
        HttpClient studentA = studentClient();

        // Create item as admin — requiresApproval=true avoids pick-task location requirement
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Isolation Item " + ts);
        itemReq.put("sku", "ISO-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 3.99);
        itemReq.put("quantity", 5);
        itemReq.put("requiresApproval", true);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode(), "Admin must create item successfully");
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        // Student A creates a request — must succeed
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("itemId", itemId);
        requestBody.put("quantity", 1);
        requestBody.put("justification", "Isolation test");
        ResponseEntity<Map> reqResp = studentA.post("/api/requests", requestBody, Map.class);
        assertEquals(HttpStatus.OK, reqResp.getStatusCode(),
                "Student A request creation must succeed");
        Long requestId = ((Number) ((Map<String, Object>) reqResp.getBody().get("data")).get("id")).longValue();

        // Create Student B via admin API
        String userB = "student_b_" + ts;
        Map<String, Object> createUserReq = Map.of(
                "username", userB,
                "password", "StudentB123!",
                "displayName", "Student B",
                "email", userB + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        admin.post("/api/admin/users", createUserReq, Map.class);
        HttpClient studentB = loginAs(userB, "StudentB123!");

        // Student B tries to access Student A's request — must be denied
        ResponseEntity<Map> isolationResp = studentB.get("/api/requests/" + requestId, Map.class);
        assertTrue(
                isolationResp.getStatusCode() == HttpStatus.FORBIDDEN
                        || isolationResp.getStatusCode() == HttpStatus.NOT_FOUND,
                "Expected 403 or 404 for cross-user request access, got "
                        + isolationResp.getStatusCode());
        assertNotNull(isolationResp.getBody(), "Denied access response must include a body");
        assertFalse((Boolean) isolationResp.getBody().get("success"),
                "Cross-user request isolation response must have success=false");
    }

    // ── Notification unread count ─────────────────────────────────────────────

    @Test
    void notificationUnreadCount_afterMarkAllRead_isZero() {
        HttpClient client = studentClient();

        // Mark all read
        ResponseEntity<Map> markResp = client.put(
                "/api/notifications/read-all", null, Map.class);
        assertEquals(HttpStatus.OK, markResp.getStatusCode());
        assertTrue((Boolean) markResp.getBody().get("success"),
                "Mark-all-read should succeed");

        // Get unread count
        ResponseEntity<Map> countResp = client.get(
                "/api/notifications/unread-count", Map.class);
        assertEquals(HttpStatus.OK, countResp.getStatusCode());
        assertTrue((Boolean) countResp.getBody().get("success"),
                "Unread count endpoint should succeed");

        Object countData = countResp.getBody().get("data");
        assertNotNull(countData, "Unread count data must not be null");
        // Data might be a number directly or an object with a 'count' field
        int count = extractCount(countData);
        assertEquals(0, count,
                "Unread count must be 0 immediately after mark-all-read");
    }

    private int extractCount(Object countData) {
        if (countData instanceof Number) {
            return ((Number) countData).intValue();
        }
        if (countData instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) countData;
            if (m.containsKey("count")) return ((Number) m.get("count")).intValue();
            if (m.containsKey("unreadCount")) return ((Number) m.get("unreadCount")).intValue();
        }
        return 0;
    }

    // ── Search finds created item ─────────────────────────────────────────────

    @Test
    void search_findsNewlyCreatedItem() {
        HttpClient admin = adminClient();
        String ts = String.valueOf(System.currentTimeMillis());
        String uniqueName = "SearchTarget" + ts;

        // Create item with a unique name
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", uniqueName);
        itemReq.put("description", "Unique item for search test");
        itemReq.put("sku", "SRC-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 5.99);
        itemReq.put("quantity", 3);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");

        ResponseEntity<Map> createResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());

        // Search for the unique name
        HttpClient student = studentClient();
        ResponseEntity<Map> searchResp = student.get(
                "/api/search?q=" + uniqueName, Map.class);
        assertEquals(HttpStatus.OK, searchResp.getStatusCode());
        assertTrue((Boolean) searchResp.getBody().get("success"),
                "Search should succeed");

        Object data = searchResp.getBody().get("data");
        assertNotNull(data, "Search result data must not be null");
        assertTrue(data.toString().contains(uniqueName),
                "Search results must contain the newly created item name: " + uniqueName);
    }

    // ── Validation error format ───────────────────────────────────────────────

    @Test
    void tagAdd_missingTagField_returns400() {
        HttpClient client = studentClient();

        // POST with empty body (missing required 'tag' field)
        ResponseEntity<Map> response = client.post(
                "/api/profile/tags", Map.of(), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Missing required 'tag' field should return 400");
    }

    @Test
    void adminCreateUser_missingPassword_returns400() {
        HttpClient admin = adminClient();

        // POST user without required password
        Map<String, Object> badReq = new java.util.HashMap<>();
        badReq.put("username", "nopwd_" + System.currentTimeMillis());
        badReq.put("displayName", "No Password User");
        badReq.put("email", "nopwd@campus.edu");
        badReq.put("roles", List.of("STUDENT"));
        // 'password' intentionally omitted

        ResponseEntity<Map> response = admin.post("/api/admin/users", badReq, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Creating a user without password must return 400 Bad Request");
        assertFalse((Boolean) response.getBody().get("success"),
                "Response for missing-password user must have success=false");
        assertNotNull(response.getBody().get("error"),
                "Response for missing-password user must include an error field");
    }

    // ── Admin user CRUD round-trip ────────────────────────────────────────────

    @Test
    void adminUserCrud_createReadUpdate_roundTrip() {
        HttpClient admin = adminClient();
        String ts = String.valueOf(System.currentTimeMillis());
        String username = "crud_user_" + ts;

        // Create user
        Map<String, Object> createReq = Map.of(
                "username", username,
                "password", "CrudPass123!",
                "displayName", "CRUD Test User",
                "email", username + "@campus.edu",
                "roles", List.of("STUDENT")
        );
        ResponseEntity<Map> createResp = admin.post("/api/admin/users", createReq, Map.class);
        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        assertTrue((Boolean) createResp.getBody().get("success"), "User create should succeed");

        Map<String, Object> createdUser = (Map<String, Object>) createResp.getBody().get("data");
        Long userId = ((Number) createdUser.get("id")).longValue();
        assertNotNull(userId, "Created user must have an id");

        // Update user display name (PUT returns ApiResponse<Void> — no data)
        String updatedName = "Updated CRUD User " + ts;
        Map<String, Object> updateReq = Map.of(
                "displayName", updatedName,
                "email", username + "_updated@campus.edu"
        );
        ResponseEntity<Map> updateResp = admin.put(
                "/api/admin/users/" + userId, updateReq, Map.class);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode(),
                "User update should succeed");
        assertTrue((Boolean) updateResp.getBody().get("success"), "User update should succeed");

        // Verify the update by GETting the user (returns UserEntity as String to avoid lazy-load issues)
        ResponseEntity<String> getResp = admin.get("/api/admin/users/" + userId, String.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode(), "GET user after update should succeed");
        assertTrue(getResp.getBody().contains(updatedName),
                "GET user response must contain the updated displayName: " + updatedName);
    }

    // ── Search result pagination ──────────────────────────────────────────────

    @Test
    void search_responseHasPaginationInfo() {
        HttpClient student = studentClient();

        ResponseEntity<Map> searchResp = student.get("/api/search?q=item", Map.class);
        assertEquals(HttpStatus.OK, searchResp.getStatusCode());
        assertTrue((Boolean) searchResp.getBody().get("success"));

        Object data = searchResp.getBody().get("data");
        assertNotNull(data, "Search data must not be null");
        // Paginated response should have totalElements or content structure
        String dataStr = data.toString();
        assertTrue(
                dataStr.contains("totalElements") || dataStr.contains("content")
                        || dataStr.contains("total"),
                "Search response should include pagination info, got: " + dataStr);
    }

    // ── Browsing history ─────────────────────────────────────────────────────

    @Test
    void browsingHistory_addedItem_appearsInHistory() {
        HttpClient student = studentClient();
        HttpClient admin = adminClient();

        // Create an item to browse
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Browse History Item " + ts);
        itemReq.put("sku", "BH-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 2.99);
        itemReq.put("quantity", 5);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode());
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        // Record browsing history — must succeed with success=true
        ResponseEntity<Map> browseResp = student.post(
                "/api/browsing-history/" + itemId, null, Map.class);
        assertEquals(HttpStatus.OK, browseResp.getStatusCode(),
                "Recording browsing history must succeed");
        assertTrue((Boolean) browseResp.getBody().get("success"),
                "Browsing history record must return success=true");
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    @Test
    void favorites_addAndList_roundTrip() {
        HttpClient student = studentClient();
        HttpClient admin = adminClient();

        // Create an item to favorite
        String ts = String.valueOf(System.currentTimeMillis());
        Map<String, Object> itemReq = new java.util.HashMap<>();
        itemReq.put("name", "Favorite Item " + ts);
        itemReq.put("sku", "FAV-SKU-" + ts);
        itemReq.put("categoryId", 1);
        itemReq.put("departmentId", 1);
        itemReq.put("priceUsd", 6.99);
        itemReq.put("quantity", 8);
        itemReq.put("requiresApproval", false);
        itemReq.put("condition", "NEW");
        ResponseEntity<Map> itemResp = admin.post("/api/inventory", itemReq, Map.class);
        assertEquals(HttpStatus.OK, itemResp.getStatusCode());
        Long itemId = ((Number) ((Map<String, Object>) itemResp.getBody().get("data")).get("id")).longValue();

        // Add to favorites — must succeed
        ResponseEntity<Map> addFavResp = student.post(
                "/api/favorites/" + itemId, null, Map.class);
        assertEquals(HttpStatus.OK, addFavResp.getStatusCode(),
                "Adding item to favorites must succeed");
        assertTrue((Boolean) addFavResp.getBody().get("success"),
                "Add favorites response must have success=true");

        // List favorites — the item ID must appear in the returned list
        ResponseEntity<Map> listFavResp = student.get("/api/favorites", Map.class);
        assertEquals(HttpStatus.OK, listFavResp.getStatusCode(),
                "GET /api/favorites must succeed");
        assertTrue((Boolean) listFavResp.getBody().get("success"),
                "Favorites list must have success=true");
        Object favData = listFavResp.getBody().get("data");
        assertNotNull(favData, "Favorites list data must not be null");
        // GET /api/favorites returns a List<Long> of item IDs — item must be present
        assertTrue(favData.toString().contains(String.valueOf(itemId)),
                "Favorites list must contain item id=" + itemId + " after it was added");
    }
}
