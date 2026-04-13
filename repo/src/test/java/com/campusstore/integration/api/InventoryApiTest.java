package com.campusstore.integration.api;

import com.campusstore.core.domain.model.ItemCondition;
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
import com.campusstore.core.domain.model.ABCClassification;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryApiTest {

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

    // ── GET /api/inventory ─────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getInventory_authenticated_returnsItems() throws Exception {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(1L);
        item.setName("Laptop");
        item.setDescription("Dell laptop");
        item.setSku("SKU-001");
        item.setPriceUsd(new BigDecimal("499.99"));
        item.setQuantityTotal(10);
        item.setQuantityAvailable(8);
        item.setIsActive(true);
        item.setAbcClassification(ABCClassification.A);
        when(inventoryService.listItems(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk());
    }

    @Test
    void getInventory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/inventory/{id} ────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void getInventoryById_authenticated_returnsItem() throws Exception {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(1L);
        item.setName("Laptop");
        item.setDescription("Dell laptop");
        item.setSku("SKU-001");
        item.setPriceUsd(new BigDecimal("499.99"));
        item.setQuantityTotal(10);
        item.setQuantityAvailable(8);
        item.setIsActive(true);
        item.setAbcClassification(ABCClassification.A);
        when(inventoryService.getItem(1L)).thenReturn(item);

        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk());
    }

    // ── POST /api/inventory ────────────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void createItem_asAdmin_returns200() throws Exception {
        InventoryItemEntity created = new InventoryItemEntity();
        created.setId(1L);
        when(inventoryService.createItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        Map<String, Object> request = Map.of(
                "name", "New Item",
                "sku", "SKU-002",
                "categoryId", 1,
                "priceUsd", 29.99,
                "condition", "NEW",
                "quantity", 10
        );

        mockMvc.perform(post("/api/inventory")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void createItem_asStudent_returns403() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "New Item",
                "sku", "SKU-002",
                "categoryId", 1,
                "priceUsd", 29.99,
                "condition", "NEW",
                "quantity", 10
        );

        mockMvc.perform(post("/api/inventory")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void createItem_withInvalidData_returns400() throws Exception {
        // Missing required fields: name, sku, categoryId, priceUsd, condition, quantity
        Map<String, Object> request = Map.of(
                "description", "Missing required fields"
        );

        mockMvc.perform(post("/api/inventory")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/inventory/{id} ─────────────────────────────────────

    @Test
    @WithMockCampusUser(roles = {"ADMIN"})
    void deleteItem_asAdmin_returns200() throws Exception {
        doNothing().when(inventoryService).deactivateItem(any(), any());

        mockMvc.perform(delete("/api/inventory/1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void deleteItem_asStudent_returns403() throws Exception {
        mockMvc.perform(delete("/api/inventory/1")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
