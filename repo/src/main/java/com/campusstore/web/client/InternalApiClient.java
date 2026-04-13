package com.campusstore.web.client;

import com.campusstore.api.dto.AddressResponse;
import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.ContactResponse;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.api.dto.ProfileResponse;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.core.domain.model.Role;
import com.campusstore.infrastructure.persistence.entity.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP-based REST API client. The Thymeleaf web layer NEVER calls domain services
 * directly — every interaction goes through this adapter, which issues real HTTP calls
 * to the application's own {@code /api/**} surface over loopback TLS. Authentication
 * and CSRF are forwarded from the inbound request via
 * {@link RestClientConfig#loopbackInterceptor loopbackInterceptor}.
 *
 * Method signatures mirror the REST contract one-for-one; each method's {@code //===}
 * comment names the exact endpoint it calls.
 */
@Component
public class InternalApiClient {

    private final RestClient rest;

    public InternalApiClient(RestClient internalApiRestClient) {
        this.rest = internalApiRestClient;
    }

    // ───────────────────────── helpers ─────────────────────────

    private static <T> T unwrap(ApiResponse<T> body) {
        if (body == null) return null;
        if (!body.isSuccess()) {
            String code = body.getError() != null ? body.getError().getCode() : "UNKNOWN";
            String msg = body.getError() != null ? body.getError().getMessage() : "API error";
            throw new ApiClientException(code, msg);
        }
        return body.getData();
    }

    private <T> T getJson(String uri, Class<T> type) {
        ApiResponse<T> resp = rest.get().uri(uri).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<T>>() {
                    @Override public java.lang.reflect.Type getType() { return paramType(ApiResponse.class, type); }
                });
        return unwrap(resp);
    }

    private <T> T getJson(String uri, ParameterizedTypeReference<ApiResponse<T>> ref) {
        ApiResponse<T> resp = rest.get().uri(uri).retrieve().body(ref);
        return unwrap(resp);
    }

    private <T> T postJson(String uri, Object body, ParameterizedTypeReference<ApiResponse<T>> ref) {
        ApiResponse<T> resp = rest.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve().body(ref);
        return unwrap(resp);
    }

    private <T> T putJson(String uri, Object body, ParameterizedTypeReference<ApiResponse<T>> ref) {
        ApiResponse<T> resp = rest.put().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve().body(ref);
        return unwrap(resp);
    }

    private void postVoid(String uri, Object body) {
        rest.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve().toBodilessEntity();
    }

    private void putVoid(String uri, Object body) {
        rest.put().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve().toBodilessEntity();
    }

    private void deleteVoid(String uri) {
        rest.delete().uri(uri).retrieve().toBodilessEntity();
    }

    private static java.lang.reflect.ParameterizedType paramType(Class<?> raw, java.lang.reflect.Type... args) {
        return new java.lang.reflect.ParameterizedType() {
            public java.lang.reflect.Type[] getActualTypeArguments() { return args; }
            public java.lang.reflect.Type getRawType() { return raw; }
            public java.lang.reflect.Type getOwnerType() { return null; }
        };
    }

    private static String appendPage(String path, Pageable p) {
        char sep = path.contains("?") ? '&' : '?';
        return path + sep + "page=" + p.getPageNumber() + "&size=" + p.getPageSize();
    }

    // ───────────────────────── inventory ─────────────────────────

    public PagedResponse<InventoryItemEntity> listItems(Pageable pageable) {
        return getJson(appendPage("/api/inventory", pageable),
                new ParameterizedTypeReference<>() {});
    }

    public InventoryItemEntity getItem(Long id) {
        return getJson("/api/inventory/" + id, new ParameterizedTypeReference<>() {});
    }

    public InventoryItemEntity createItem(String name, String desc, String sku, Long catId, Long deptId,
                                           Long locId, BigDecimal price, int qtyTotal,
                                           boolean requiresApproval, Long actorId,
                                           ItemCondition condition, LocalDate expDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", desc);
        body.put("sku", sku != null ? sku : ("AUTO-" + System.currentTimeMillis()));
        body.put("categoryId", catId);
        body.put("departmentId", deptId);
        body.put("locationId", locId);
        body.put("priceUsd", price);
        body.put("quantity", qtyTotal);
        body.put("requiresApproval", requiresApproval);
        body.put("condition", condition != null ? condition.name() : ItemCondition.NEW.name());
        if (expDate != null) body.put("expirationDate", expDate.toString());
        return postJson("/api/inventory", body, new ParameterizedTypeReference<>() {});
    }

    public InventoryItemEntity updateItem(Long id, String name, String desc, BigDecimal price,
                                           Integer qtyTotal, Boolean requiresApproval, Long actorId) {
        Map<String, Object> body = new HashMap<>();
        if (name != null) body.put("name", name);
        if (desc != null) body.put("description", desc);
        if (price != null) body.put("priceUsd", price);
        if (qtyTotal != null) body.put("quantity", qtyTotal);
        if (requiresApproval != null) body.put("requiresApproval", requiresApproval);
        return putJson("/api/inventory/" + id, body, new ParameterizedTypeReference<>() {});
    }

    public void deactivateItem(Long id, Long actorId) {
        deleteVoid("/api/inventory/" + id);
    }

    // ───────────────────────── search & favorites ─────────────────────────

    public PagedResponse<InventoryItemEntity> search(String keyword, Long categoryId, Long departmentId,
                                             Long userZoneId, String sort, boolean personalized, Long userId,
                                             Pageable pageable, BigDecimal priceMin, BigDecimal priceMax,
                                             ItemCondition condition, Long zoneId) {
        StringBuilder q = new StringBuilder("/api/search?page=").append(pageable.getPageNumber())
                .append("&size=").append(pageable.getPageSize());
        if (keyword != null && !keyword.isBlank()) q.append("&q=").append(urlEncode(keyword));
        if (categoryId != null) q.append("&categoryId=").append(categoryId);
        if (departmentId != null) q.append("&departmentId=").append(departmentId);
        if (zoneId != null) q.append("&zoneId=").append(zoneId);
        if (priceMin != null) q.append("&priceMin=").append(priceMin);
        if (priceMax != null) q.append("&priceMax=").append(priceMax);
        if (condition != null) q.append("&condition=").append(condition.name());
        if (sort != null) q.append("&sort=").append(urlEncode(sort));
        if (personalized) q.append("&personalized=true");
        return getJson(q.toString(), new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> getTrendingTerms() {
        return getJson("/api/search/trending", new ParameterizedTypeReference<>() {});
    }

    public List<FavoriteEntity> getFavorites(Long userId) {
        return getJson("/api/favorites", new ParameterizedTypeReference<>() {});
    }

    public void addFavorite(Long userId, Long itemId) {
        postVoid("/api/favorites/" + itemId, null);
    }

    public void removeFavorite(Long userId, Long itemId) {
        deleteVoid("/api/favorites/" + itemId);
    }

    // ───────────────────────── requests ─────────────────────────

    public ItemRequestEntity createRequest(Long userId, Long itemId, int qty, String justification) {
        Map<String, Object> body = Map.of(
                "itemId", itemId,
                "quantity", qty,
                "justification", justification != null ? justification : "Requested via web UI");
        return postJson("/api/requests", body, new ParameterizedTypeReference<>() {});
    }

    public void approveRequest(Long id, Long userId) {
        putVoid("/api/requests/" + id + "/approve", null);
    }

    public void rejectRequest(Long id, Long userId, String reason) {
        putVoid("/api/requests/" + id + "/reject",
                Map.of("reason", reason != null ? reason : "Rejected"));
    }

    public void cancelRequest(Long id, Long userId) {
        putVoid("/api/requests/" + id + "/cancel", null);
    }

    public void markPickedUp(Long id, Long userId) {
        putVoid("/api/requests/" + id + "/picked-up", null);
    }

    public ItemRequestEntity getRequest(Long id, Long userId) {
        return getJson("/api/requests/" + id, new ParameterizedTypeReference<>() {});
    }

    public PagedResponse<ItemRequestEntity> listMyRequests(Long userId, Pageable p) {
        return getJson(appendPage("/api/requests/mine", p), new ParameterizedTypeReference<>() {});
    }

    public PagedResponse<ItemRequestEntity> listPendingApprovals(Long userId, Pageable p) {
        return getJson(appendPage("/api/requests/pending-approval", p),
                new ParameterizedTypeReference<>() {});
    }

    // ───────────────────────── notifications ─────────────────────────

    public PagedResponse<NotificationEntity> listNotifications(Long userId, Pageable p) {
        return getJson(appendPage("/api/notifications", p),
                new ParameterizedTypeReference<>() {});
    }

    public long getUnreadCount(Long userId) {
        Number n = getJson("/api/notifications/unread-count",
                new ParameterizedTypeReference<ApiResponse<Number>>() {});
        return n == null ? 0L : n.longValue();
    }

    public void markRead(Long id, Long userId) {
        putVoid("/api/notifications/" + id + "/read", null);
    }

    public void markAllRead(Long userId) {
        putVoid("/api/notifications/read-all", null);
    }

    public List<NotificationPreferenceEntity> getNotificationPreferences(Long userId) {
        return getJson("/api/notification-preferences", new ParameterizedTypeReference<>() {});
    }

    // ───────────────────────── profile ─────────────────────────

    public ProfileResponse getProfile(Long userId) {
        return getJson("/api/profile", new ParameterizedTypeReference<>() {});
    }

    public List<AddressResponse> getAddresses(Long userId) {
        return getJson("/api/profile/addresses", new ParameterizedTypeReference<>() {});
    }

    public void addAddress(Long userId, String label, String street, String city, String state, String zip) {
        Map<String, Object> body = Map.of(
                "label", label,
                "street", street,
                "city", city,
                "state", state,
                "zipCode", zip);
        postVoid("/api/profile/addresses", body);
    }

    public void deleteAddress(Long userId, Long addressId) {
        deleteVoid("/api/profile/addresses/" + addressId);
    }

    public List<String> getTags(Long userId) {
        return getJson("/api/profile/tags", new ParameterizedTypeReference<>() {});
    }

    public void addTag(Long userId, String tag) {
        postVoid("/api/profile/tags", Map.of("tag", tag));
    }

    public void removeTag(Long userId, String tag) {
        deleteVoid("/api/profile/tags/" + urlEncode(tag));
    }

    public List<ContactResponse> getContacts(Long userId) {
        return getJson("/api/profile/contacts", new ParameterizedTypeReference<>() {});
    }

    public void addContact(Long userId, String label, String relationship, String name,
                             String email, String phone, String notes) {
        Map<String, Object> body = new HashMap<>();
        body.put("label", label);
        if (relationship != null) body.put("relationship", relationship);
        if (name != null) body.put("name", name);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        if (notes != null) body.put("notes", notes);
        postVoid("/api/profile/contacts", body);
    }

    public void updateContact(Long userId, Long contactId, String label, String relationship,
                                String name, String email, String phone, String notes) {
        Map<String, Object> body = new HashMap<>();
        body.put("label", label);
        if (relationship != null) body.put("relationship", relationship);
        if (name != null) body.put("name", name);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        if (notes != null) body.put("notes", notes);
        putVoid("/api/profile/contacts/" + contactId, body);
    }

    public void deleteContact(Long userId, Long contactId) {
        deleteVoid("/api/profile/contacts/" + contactId);
    }

    public UserPreferenceEntity getPreferences(Long userId) {
        return getJson("/api/user/preferences", new ParameterizedTypeReference<>() {});
    }

    public void updateProfile(Long userId, String displayName, String email, String phone) {
        Map<String, Object> body = new HashMap<>();
        if (displayName != null) body.put("displayName", displayName);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        putVoid("/api/profile", body);
    }

    // ───────────────────────── admin ─────────────────────────

    public PagedResponse<UserEntity> listUsers(Pageable p) {
        return getJson(appendPage("/api/admin/users", p), new ParameterizedTypeReference<>() {});
    }

    public UserEntity getUserById(Long id) {
        return getJson("/api/admin/users/" + id, new ParameterizedTypeReference<>() {});
    }

    public List<CategoryEntity> listCategories() {
        return getJson("/api/categories", new ParameterizedTypeReference<>() {});
    }

    public List<DepartmentEntity> listDepartments() {
        return getJson("/api/admin/departments", new ParameterizedTypeReference<>() {});
    }

    public List<ZoneEntity> listZones() {
        return getJson("/api/admin/zones", new ParameterizedTypeReference<>() {});
    }

    public PagedResponse<StorageLocationEntity> listLocations(Pageable p) {
        return getJson(appendPage("/api/warehouse/locations", p),
                new ParameterizedTypeReference<>() {});
    }

    public List<CrawlerJobEntity> listCrawlerJobs() {
        return getJson("/api/admin/crawler/jobs", new ParameterizedTypeReference<>() {});
    }

    public byte[] exportOutbox() {
        return rest.post().uri("/api/admin/email-outbox/export")
                .retrieve().body(byte[].class);
    }

    public UserEntity createUser(String username, String password, String displayName, String email,
                                  String phone, Role role, Long actorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("displayName", displayName);
        body.put("email", email != null ? email : "user@example.com");
        if (phone != null) body.put("phone", phone);
        body.put("roles", List.of(role != null ? role.name() : Role.STUDENT.name()));
        return postJson("/api/admin/users", body, new ParameterizedTypeReference<>() {});
    }

    public UserEntity updateUser(Long id, String displayName, String email, String phone,
                                  Long departmentId, Long actorId) {
        Map<String, Object> body = new HashMap<>();
        if (displayName != null) body.put("displayName", displayName);
        if (email != null) body.put("email", email);
        if (phone != null) body.put("phone", phone);
        if (departmentId != null) body.put("departmentId", departmentId);
        return putJson("/api/admin/users/" + id, body, new ParameterizedTypeReference<>() {});
    }

    public void assignRole(Long userId, Role role, Long actorId) {
        putVoid("/api/admin/users/" + userId + "/roles",
                Map.of("roles", List.of(role.name())));
    }

    public PagedResponse<AuditLogEntity> queryAudit(String action, String entityType, Long entityId,
                                            Long actorUserId, Instant from, Instant to, Pageable p) {
        StringBuilder q = new StringBuilder("/api/admin/audit?page=").append(p.getPageNumber())
                .append("&size=").append(p.getPageSize());
        if (action != null) q.append("&action=").append(urlEncode(action));
        if (entityType != null) q.append("&entityType=").append(urlEncode(entityType));
        if (entityId != null) q.append("&entityId=").append(entityId);
        if (actorUserId != null) q.append("&actorUserId=").append(actorUserId);
        if (from != null) q.append("&from=").append(from);
        if (to != null) q.append("&to=").append(to);
        return getJson(q.toString(), new ParameterizedTypeReference<>() {});
    }

    public PagedResponse<EmailOutboxEntity> listOutbox(Pageable p) {
        return getJson(appendPage("/api/admin/email-outbox", p),
                new ParameterizedTypeReference<>() {});
    }

    // ───────────────────────── policy administration ─────────────────────────
    // Backed by /api/admin/policies — admins manage data_retention_policy rows.

    public List<DataRetentionPolicyEntity> listPolicies() {
        return getJson("/api/admin/policies", new ParameterizedTypeReference<>() {});
    }

    public DataRetentionPolicyEntity updatePolicy(String entityType, int retentionDays,
                                                    String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("retentionDays", retentionDays);
        if (description != null) body.put("description", description);
        return putJson("/api/admin/policies/" + urlEncode(entityType), body,
                new ParameterizedTypeReference<>() {});
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Thrown when the API returns a non-success ApiResponse. */
    public static class ApiClientException extends RuntimeException {
        private final String code;
        public ApiClientException(String code, String message) {
            super("[" + code + "] " + message);
            this.code = code;
        }
        public String getCode() { return code; }
    }
}
