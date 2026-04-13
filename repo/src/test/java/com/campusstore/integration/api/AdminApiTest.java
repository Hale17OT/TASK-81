package com.campusstore.integration.api;

import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.domain.model.Role;
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
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.integration.support.WithMockCampusUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    // ── Admin endpoints require ADMIN role ──────────────────────────────

    @Test
    void adminUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void adminUsers_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void adminUsers_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/users ───────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void getUsers_asAdmin_returns200() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("Admin User");
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(userManagementService.listUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    // ── POST /api/admin/users ──────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void createUser_asAdmin_returns200() throws Exception {
        UserEntity created = new UserEntity();
        created.setId(1L);
        // Controller now uses the 8-arg createUser overload (adds departmentId).
        when(userManagementService.createUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        Map<String, Object> request = Map.of(
                "username", "newstudent",
                "password", "securePass123",
                "displayName", "New Student",
                "email", "newstudent@campus.edu",
                "roles", List.of("STUDENT")
        );

        mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void createUser_asStudent_returns403() throws Exception {
        Map<String, Object> request = Map.of(
                "username", "newstudent",
                "password", "securePass123",
                "displayName", "New Student",
                "email", "newstudent@campus.edu",
                "roles", List.of("STUDENT")
        );

        mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/audit ───────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void getAudit_asAdmin_returns200() throws Exception {
        when(auditService.query(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getAudit_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void getAudit_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isForbidden());
    }

    // ── Warehouse endpoints require ADMIN ──────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void warehouseLocations_asAdmin_returns200() throws Exception {
        when(warehouseService.listLocations(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/warehouse/locations"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void warehouseLocations_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/warehouse/locations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void warehouseLocations_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/warehouse/locations"))
                .andExpect(status().isForbidden());
    }

    // ── Crawler endpoints require ADMIN ────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void crawlerJobs_asAdmin_returns200() throws Exception {
        when(crawlerService.listJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/crawler/jobs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void crawlerJobs_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/crawler/jobs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void crawlerJobs_asTeacher_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/crawler/jobs"))
                .andExpect(status().isForbidden());
    }

    // ── Unauthenticated access to all admin endpoints ──────────────────

    @Test
    void adminAudit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void warehouseLocations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/warehouse/locations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crawlerJobs_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/crawler/jobs"))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/warehouse/simulate response-shape contract (endpoint-level) ──
    // Complements WarehouseSimulationSchemaTest: asserts the ApiResponse wrapper
    // preserves the simulation report keys end-to-end for both the empty (no
    // completed picks) and populated branches.

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void warehouseSimulate_emptyBranch_exposesFullSchemaUnderData() throws Exception {
        // Mirror the real empty-branch shape (see WarehouseService.runSimulation).
        java.util.LinkedHashMap<String, Object> emptyReport = new java.util.LinkedHashMap<>();
        emptyReport.put("orderCount", 0);
        emptyReport.put("avgStepsSaved", 0.0);
        emptyReport.put("totalCostSaved", 0.0);
        emptyReport.put("totalTimeSaved", 0.0);
        emptyReport.put("proposedVsActualRatio", 1.0);
        emptyReport.put("improvements", List.of());
        when(warehouseService.runSimulation(anyString())).thenReturn(emptyReport);

        String body = "{\"proposedLevelMultiplier\":2.0}";
        mockMvc.perform(post("/api/warehouse/simulate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCount").exists())
                .andExpect(jsonPath("$.data.avgStepsSaved").exists())
                .andExpect(jsonPath("$.data.totalCostSaved").exists())
                .andExpect(jsonPath("$.data.proposedVsActualRatio").exists())
                .andExpect(jsonPath("$.data.improvements").isArray());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void warehouseSimulate_populatedBranch_exposesFullSchemaUnderData() throws Exception {
        java.util.LinkedHashMap<String, Object> populatedReport = new java.util.LinkedHashMap<>();
        populatedReport.put("orderCount", 3);
        populatedReport.put("avgStepsSaved", 1.25);
        populatedReport.put("totalCostSaved", 3.75);
        populatedReport.put("totalTimeSaved", 3.75);
        populatedReport.put("proposedVsActualRatio", 0.82);
        populatedReport.put("improvements", List.of(Map.of("requestId", 42, "savings", 1.1)));
        when(warehouseService.runSimulation(anyString())).thenReturn(populatedReport);

        String body = "{\"proposedLevelMultiplier\":1.5}";
        mockMvc.perform(post("/api/warehouse/simulate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCount").value(3))
                .andExpect(jsonPath("$.data.avgStepsSaved").value(1.25))
                .andExpect(jsonPath("$.data.totalCostSaved").value(3.75))
                .andExpect(jsonPath("$.data.proposedVsActualRatio").value(0.82))
                .andExpect(jsonPath("$.data.improvements[0].requestId").value(42));
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void warehouseSimulate_asTeacher_returns403() throws Exception {
        mockMvc.perform(post("/api/warehouse/simulate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposedLevelMultiplier\":2.0}"))
                .andExpect(status().isForbidden());
    }
}
