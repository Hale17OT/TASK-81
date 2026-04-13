package com.campusstore.integration.api;

import com.campusstore.core.domain.model.RequestStatus;
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
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestApiTest {

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

    private ItemRequestEntity createSampleRequestEntity(Long id, RequestStatus status) {
        ItemRequestEntity entity = new ItemRequestEntity();
        entity.setId(id);
        entity.setQuantity(1);
        entity.setStatus(status);
        entity.setJustification("Need for coursework");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    // ── POST /api/requests ─────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void createRequest_withValidData_returns200() throws Exception {
        ItemRequestEntity created = createSampleRequestEntity(1L, RequestStatus.PENDING_APPROVAL);
        when(requestService.createRequest(anyLong(), eq(1L), eq(1), eq("Need for coursework")))
                .thenReturn(created);

        Map<String, Object> request = Map.of(
                "itemId", 1,
                "quantity", 1,
                "justification", "Need for coursework"
        );

        mockMvc.perform(post("/api/requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void createRequest_unauthenticated_returns401() throws Exception {
        Map<String, Object> request = Map.of(
                "itemId", 1,
                "quantity", 1,
                "justification", "Need for coursework"
        );

        mockMvc.perform(post("/api/requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/requests/{id}/approve ─────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void approveRequest_asTeacher_returns200() throws Exception {
        ItemRequestEntity approved = createSampleRequestEntity(1L, RequestStatus.APPROVED);
        when(requestService.approveRequest(eq(1L), anyLong())).thenReturn(approved);

        mockMvc.perform(put("/api/requests/1/approve")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void approveRequest_asStudent_returns403() throws Exception {
        mockMvc.perform(put("/api/requests/1/approve")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/requests/{id}/reject ──────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void rejectRequest_withReason_returns200() throws Exception {
        ItemRequestEntity rejected = createSampleRequestEntity(1L, RequestStatus.REJECTED);
        when(requestService.rejectRequest(eq(1L), anyLong(), any())).thenReturn(rejected);

        Map<String, Object> body = Map.of(
                "reason", "Insufficient justification"
        );

        mockMvc.perform(put("/api/requests/1/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void rejectRequest_asStudent_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "reason", "Insufficient justification"
        );

        mockMvc.perform(put("/api/requests/1/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/requests/{id}/cancel ──────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void cancelRequest_byRequester_returns200() throws Exception {
        ItemRequestEntity cancelled = createSampleRequestEntity(1L, RequestStatus.CANCELLED);
        when(requestService.cancelRequest(eq(1L), anyLong())).thenReturn(cancelled);

        mockMvc.perform(put("/api/requests/1/cancel")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ── GET /api/requests/mine ─────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getMyRequests_returns200() throws Exception {
        when(requestService.listMyRequests(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        createSampleRequestEntity(1L, RequestStatus.PENDING_APPROVAL)
                )));

        mockMvc.perform(get("/api/requests/mine"))
                .andExpect(status().isOk());
    }

    @Test
    void getMyRequests_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/requests/mine"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/requests/pending-approval ─────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void getPendingApproval_asTeacher_returns200() throws Exception {
        when(requestService.listPendingApprovals(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        createSampleRequestEntity(1L, RequestStatus.PENDING_APPROVAL)
                )));

        mockMvc.perform(get("/api/requests/pending-approval"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getPendingApproval_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/requests/pending-approval"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void getPendingApproval_asAdmin_returns200() throws Exception {
        when(requestService.listPendingApprovals(anyLong(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/requests/pending-approval"))
                .andExpect(status().isOk());
    }

    // ── PUT /api/requests/{id}/start-picking ───────────────────────────

    @Test
    void startPicking_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/requests/1/start-picking").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void startPicking_asStudent_returns403() throws Exception {
        mockMvc.perform(put("/api/requests/1/start-picking").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void startPicking_asTeacher_returns403() throws Exception {
        // Picking workflow is warehouse/admin-only; teachers cannot advance fulfillment state.
        mockMvc.perform(put("/api/requests/1/start-picking").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void startPicking_asAdmin_returns200_andTransitionsState() throws Exception {
        ItemRequestEntity picking = createSampleRequestEntity(1L, RequestStatus.PICKING);
        when(requestService.startPicking(eq(1L), anyLong())).thenReturn(picking);

        mockMvc.perform(put("/api/requests/1/start-picking").with(csrf()))
                .andExpect(status().isOk());

        // Confirm the service saw the state-transition call (not a silent no-op).
        org.mockito.Mockito.verify(requestService).startPicking(eq(1L), anyLong());
    }

    // ── PUT /api/requests/{id}/ready-for-pickup ────────────────────────

    @Test
    void readyForPickup_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/requests/1/ready-for-pickup").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void readyForPickup_asStudent_returns403() throws Exception {
        mockMvc.perform(put("/api/requests/1/ready-for-pickup").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"TEACHER"})
    void readyForPickup_asTeacher_returns403() throws Exception {
        mockMvc.perform(put("/api/requests/1/ready-for-pickup").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void readyForPickup_asAdmin_returns200_andTransitionsState() throws Exception {
        ItemRequestEntity ready = createSampleRequestEntity(1L, RequestStatus.READY_FOR_PICKUP);
        when(requestService.markReadyForPickup(eq(1L), anyLong())).thenReturn(ready);

        mockMvc.perform(put("/api/requests/1/ready-for-pickup").with(csrf()))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(requestService).markReadyForPickup(eq(1L), anyLong());
    }
}
