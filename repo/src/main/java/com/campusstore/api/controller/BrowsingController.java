package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.core.service.SearchService;
import com.campusstore.infrastructure.persistence.entity.FavoriteEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BrowsingController {

    private final SearchService searchService;

    public BrowsingController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/browsing-history/{itemId}")
    public ResponseEntity<ApiResponse<Void>> recordBrowse(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long itemId) {
        Long userId = principal != null ? principal.getUserId() : null;
        searchService.recordBrowse(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<Long>>> listFavorites(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        List<FavoriteEntity> favorites = searchService.getFavorites(userId);
        List<Long> favoriteItemIds = favorites.stream()
                .map(FavoriteEntity::getItemId)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(favoriteItemIds));
    }

    @PostMapping("/favorites/{itemId}")
    public ResponseEntity<ApiResponse<Void>> addFavorite(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long itemId) {
        Long userId = principal != null ? principal.getUserId() : null;
        searchService.addFavorite(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/favorites/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long itemId) {
        Long userId = principal != null ? principal.getUserId() : null;
        searchService.removeFavorite(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
