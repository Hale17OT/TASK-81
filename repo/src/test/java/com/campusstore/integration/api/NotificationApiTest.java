package com.campusstore.integration.api;

import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.CategoryService;
import com.campusstore.core.service.CrawlerService;
import com.campusstore.core.service.DepartmentService;
import com.campusstore.core.service.EmailOutboxService;
import com.campusstore.core.service.InventoryService;
import com.campusstore.core.service.NotificationService;
import com.campusstore.core.service.ProfileService;
import com.campusstore.core.service.RequestService;
import com.campusstore.core.service.SearchService;
import com.campusstore.core.service.UserManagementService;
import com.campusstore.core.service.WarehouseService;
import com.campusstore.core.service.ZoneService;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.integration.support.WithMockCampusUser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private RequestService requestService;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private UserManagementService userManagementService;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private CrawlerService crawlerService;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private EmailOutboxService emailOutboxService;

    @MockitoBean
    private ZoneService zoneService;

    // ── GET /api/notifications ─────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getNotifications_authenticated_returns200() throws Exception {
        NotificationEntity notification = new NotificationEntity();
        notification.setId(1L);
        notification.setType(NotificationType.REQUEST_APPROVED);
        notification.setTitle("Request Approved");
        notification.setMessage("Your request has been approved");
        notification.setIsRead(false);
        notification.setIsCritical(false);
        notification.setReferenceType("REQUEST");
        notification.setReferenceId(1L);
        notification.setCreatedAt(Instant.now());
        when(notificationService.listNotifications(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(notification)));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk());
    }

    @Test
    void getNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/notifications/unread-count ─────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getUnreadCount_authenticated_returns200() throws Exception {
        when(notificationService.getUnreadCount(anyLong())).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk());
    }

    @Test
    void getUnreadCount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/notifications/{id}/read ───────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void markRead_authenticated_returns200() throws Exception {
        doNothing().when(notificationService).markRead(eq(1L), anyLong());

        mockMvc.perform(put("/api/notifications/1/read")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void markRead_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/notifications/1/read")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/notifications/read-all ────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void markAllRead_authenticated_returns200() throws Exception {
        doNothing().when(notificationService).markAllRead(anyLong());

        mockMvc.perform(put("/api/notifications/read-all")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void markAllRead_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── Cross-role access ──────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void getNotifications_asAdmin_returns200() throws Exception {
        when(notificationService.listNotifications(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void getNotifications_asTeacher_returns200() throws Exception {
        when(notificationService.listNotifications(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk());
    }
}
