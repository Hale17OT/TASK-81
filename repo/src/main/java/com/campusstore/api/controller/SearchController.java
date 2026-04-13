package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.core.service.SearchService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.SearchLogEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<InventoryItemEntity>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean personalized,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        // Personalization requires both an authenticated user AND an explicit opt-in via the
        // request param (mirrors the UI toggle). Anonymous callers can never trigger it.
        // SearchService additionally honors the user's persisted preference (kill-switch).
        boolean personalizationEnabled = userId != null && personalized;

        // Resolve user's home zone for distance sorting (distinct from zoneId filter)
        Long userZoneId = principal != null ? principal.getHomeZoneId() : null;

        Page<InventoryItemEntity> result = searchService.search(
                q, categoryId, departmentId, userZoneId, sort,
                personalizationEnabled, userId,
                PageRequest.of(page, size),
                priceMin, priceMax, condition, zoneId
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTrending() {
        List<Map<String, Object>> trending = searchService.getTrendingTerms();
        return ResponseEntity.ok(ApiResponse.success(trending));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PagedResponse<SearchLogEntity>>> getHistory(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = principal != null ? principal.getUserId() : null;
        Page<SearchLogEntity> result = searchService.getSearchHistory(
                userId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }
}
