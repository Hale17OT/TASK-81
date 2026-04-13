package com.campusstore.web.controller;

import com.campusstore.api.dto.PagedResponse;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller("webSearchController")
public class SearchController {

    private final InternalApiClient apiClient;

    public SearchController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal CampusUserPrincipal principal,
                         @RequestParam(value = "keyword", required = false) String keyword,
                         @RequestParam(value = "category", required = false) Long categoryId,
                         @RequestParam(value = "department", required = false) Long departmentId,
                         @RequestParam(value = "zone", required = false) Long zoneId,
                         @RequestParam(value = "priceMin", required = false) BigDecimal priceMin,
                         @RequestParam(value = "priceMax", required = false) BigDecimal priceMax,
                         @RequestParam(value = "condition", required = false) String condition,
                         @RequestParam(value = "sort", defaultValue = "newest") String sort,
                         @RequestParam(value = "personalized", defaultValue = "false") boolean personalized,
                         @RequestParam(value = "page", defaultValue = "0") int page,
                         @RequestParam(value = "size", defaultValue = "12") int size,
                         Model model) {
        model.addAttribute("currentPage", "search");

        Long userId = principal != null ? principal.getUserId() : null;

        // Parse condition string to enum (if provided)
        ItemCondition itemCondition = null;
        if (condition != null && !condition.isBlank()) {
            try {
                itemCondition = ItemCondition.valueOf(condition);
            } catch (IllegalArgumentException ignored) {
                // Invalid condition value, ignore filter
            }
        }

        // Normalize sort tokens: template sends "price-asc"/"price-desc" but service expects "price_asc"/"price_desc"
        String normalizedSort = sort.replace('-', '_');

        // For distance sort, use the user's home zone (not the filter zone)
        Long userHomeZoneId = principal != null ? principal.getHomeZoneId() : null;

        PagedResponse<InventoryItemEntity> results = apiClient.search(
                keyword, categoryId, departmentId, userHomeZoneId, normalizedSort,
                personalized, userId,
                PageRequest.of(page, size),
                priceMin, priceMax, itemCondition, zoneId
        );
        List<Map<String, Object>> trendingTerms = apiClient.getTrendingTerms();

        model.addAttribute("trendingTerms", trendingTerms);
        model.addAttribute("results", results);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("selectedZone", zoneId);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("personalized", personalized);
        model.addAttribute("selectedPriceMin", priceMin);
        model.addAttribute("selectedPriceMax", priceMax);
        model.addAttribute("selectedCondition", condition);

        // Populate filter dropdown data
        model.addAttribute("categories", apiClient.listCategories());
        model.addAttribute("zones", apiClient.listZones());
        model.addAttribute("conditions", ItemCondition.values());

        return "home/search";
    }
}
