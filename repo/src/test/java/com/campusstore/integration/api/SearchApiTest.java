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
import com.campusstore.infrastructure.persistence.entity.FavoriteEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.SearchLogEntity;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchApiTest {

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

    // ── GET /api/search (public) ───────────────────────────────────────

    @Test
    void search_withQuery_returnsResults_public() throws Exception {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(1L);
        item.setName("Laptop");
        item.setDescription("Dell laptop");
        item.setPriceUsd(new BigDecimal("499.99"));
        item.setQuantityAvailable(5);
        item.setIsActive(true);
        when(searchService.search(any(), any(), any(), any(), any(), any(boolean.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/search").param("q", "test"))
                .andExpect(status().isOk());
    }

    @Test
    void search_withoutAuth_stillReturnsResults() throws Exception {
        when(searchService.search(any(), any(), any(), any(), any(), any(boolean.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/search").param("q", "laptop"))
                .andExpect(status().isOk());
    }

    // ── Regression: keyword query param contract ─────────────────────────
    // The web layer's search bar field is named "keyword" but the canonical API
    // contract uses "q". InternalApiClient must translate the former into the
    // latter; this test pins the API side of that contract.
    @Test
    void search_acceptsCanonicalQParam_andRejectsLegacyKeywordParam() throws Exception {
        // Stub for both invocations regardless of the first-arg value.
        when(searchService.search(any(), any(), any(), any(), any(),
                any(boolean.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // q is the canonical contract — service receives the value
        mockMvc.perform(get("/api/search").param("q", "laptop"))
                .andExpect(status().isOk());
        verify(searchService).search(eq("laptop"), any(), any(), any(), any(),
                any(boolean.class), any(), any(), any(), any(), any(), any());

        // legacy "keyword" param is silently ignored — service is invoked with null
        mockMvc.perform(get("/api/search").param("keyword", "laptop"))
                .andExpect(status().isOk());
        verify(searchService).search(eq(null), any(), any(), any(), any(),
                any(boolean.class), any(), any(), any(), any(), any(), any());
    }

    // ── Regression: personalization toggle is honored, not hardcoded ─────
    @Test
    void search_anonymous_neverEnablesPersonalization() throws Exception {
        when(searchService.search(any(), any(), any(), any(), any(), any(boolean.class),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/search").param("q", "x").param("personalized", "true"))
                .andExpect(status().isOk());
        // Anonymous => personalization MUST be false regardless of param
        verify(searchService).search(any(), any(), any(), any(), any(),
                eq(false), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void search_authenticated_personalizationDefaultsOff() throws Exception {
        when(searchService.search(any(), any(), any(), any(), any(), any(boolean.class),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/search").param("q", "x"))
                .andExpect(status().isOk());
        // No toggle => off (the previous bug always enabled it for any auth user)
        verify(searchService).search(any(), any(), any(), any(), any(),
                eq(false), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void search_authenticated_personalizationHonoredWhenToggled() throws Exception {
        when(searchService.search(any(), any(), any(), any(), any(), any(boolean.class),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/search").param("q", "x").param("personalized", "true"))
                .andExpect(status().isOk());
        verify(searchService).search(any(), any(), any(), any(), any(),
                eq(true), any(), any(), any(), any(), any(), any());
    }

    // ── GET /api/search/trending (public) ──────────────────────────────

    @Test
    void searchTrending_returnsTerms_public() throws Exception {
        when(searchService.getTrendingTerms()).thenReturn(List.of(
                Map.of("term", "laptop", "count", 10L),
                Map.of("term", "textbook", "count", 8L),
                Map.of("term", "calculator", "count", 5L)
        ));

        mockMvc.perform(get("/api/search/trending"))
                .andExpect(status().isOk());
    }

    @Test
    void searchTrending_withoutAuth_returns200() throws Exception {
        when(searchService.getTrendingTerms()).thenReturn(List.of());

        mockMvc.perform(get("/api/search/trending"))
                .andExpect(status().isOk());
    }

    // ── GET /api/search/history (authenticated) ────────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void searchHistory_authenticated_returns200() throws Exception {
        SearchLogEntity logEntry = new SearchLogEntity();
        logEntry.setId(1L);
        logEntry.setQueryText("laptop");
        logEntry.setResultCount(5);
        logEntry.setSearchedAt(Instant.now());
        when(searchService.getSearchHistory(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(logEntry)));

        mockMvc.perform(get("/api/search/history"))
                .andExpect(status().isOk());
    }

    @Test
    void searchHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/search/history"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/browsing-history/{id} (authenticated) ────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void recordBrowse_authenticated_returns200() throws Exception {
        doNothing().when(searchService).recordBrowse(anyLong(), eq(1L));

        mockMvc.perform(post("/api/browsing-history/1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void recordBrowse_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/browsing-history/1")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/favorites/{id} (authenticated) ───────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void addFavorite_authenticated_returns200() throws Exception {
        FavoriteEntity favorite = new FavoriteEntity();
        favorite.setId(1L);
        when(searchService.addFavorite(anyLong(), eq(1L))).thenReturn(favorite);

        mockMvc.perform(post("/api/favorites/1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void addFavorite_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/favorites/1")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/favorites/{id} (authenticated) ─────────────────────

    @Test
    @WithMockCampusUser(roles = {"STUDENT"})
    void removeFavorite_authenticated_returns200() throws Exception {
        doNothing().when(searchService).removeFavorite(anyLong(), eq(1L));

        mockMvc.perform(delete("/api/favorites/1")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void removeFavorite_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/favorites/1")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
