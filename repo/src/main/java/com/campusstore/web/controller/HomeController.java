package com.campusstore.web.controller;

import com.campusstore.api.dto.PagedResponse;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final InternalApiClient apiClient;

    public HomeController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal CampusUserPrincipal principal,
                       Model model) {
        model.addAttribute("currentPage", "home");
        model.addAttribute("displayName", principal.getDisplayName());

        // Trending search terms
        List<Map<String, Object>> trendingTerms = apiClient.getTrendingTerms();
        model.addAttribute("trendingTerms", trendingTerms);

        // Recent items for the home grid
        PagedResponse<InventoryItemEntity> recentItems = apiClient.listItems(PageRequest.of(0, 12));
        model.addAttribute("recentItems", recentItems);

        return "home/index";
    }
}
