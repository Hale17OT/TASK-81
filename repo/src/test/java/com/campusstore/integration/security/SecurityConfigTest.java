package com.campusstore.integration.security;

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
import com.campusstore.integration.support.WithMockCampusUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

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

    @BeforeEach
    void setUpDefaultMocks() {
        when(inventoryService.listItems(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(searchService.search(any(), any(), any(), any(), any(), any(boolean.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(searchService.getTrendingTerms())
                .thenReturn(List.of());
        when(userManagementService.listUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(requestService.listPendingApprovals(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    // ── Unauthenticated access ─────────────────────────────────────────

    @Test
    void unauthenticated_apiInventory_returns401() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticated_apiSearch_returns200() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_apiSearchTrending_returns200() throws Exception {
        mockMvc.perform(get("/api/search/trending"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_loginPage_returns200() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_staticCss_returns200() throws Exception {
        mockMvc.perform(get("/css/style.css"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_staticJs_returns200() throws Exception {
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk());
    }

    // ── Student access ─────────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void student_apiInventory_returns200() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void student_apiAdminUsers_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void student_apiRequestsPendingApproval_returns403() throws Exception {
        mockMvc.perform(get("/api/requests/pending-approval"))
                .andExpect(status().isForbidden());
    }

    // ── Admin access ───────────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void admin_apiAdminUsers_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void admin_apiInventory_returns200() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk());
    }

    // ── Teacher access ─────────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void teacher_apiRequestsPendingApproval_returns200() throws Exception {
        mockMvc.perform(get("/api/requests/pending-approval"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void teacher_apiAdminUsers_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}
