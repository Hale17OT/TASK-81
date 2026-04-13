package com.campusstore.web.controller;

import com.campusstore.infrastructure.persistence.entity.FavoriteEntity;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class InventoryWebController {

    private final InternalApiClient apiClient;

    public InventoryWebController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping("/inventory/{id}")
    public String itemDetail(@PathVariable Long id,
                             @AuthenticationPrincipal CampusUserPrincipal principal,
                             Model model) {
        model.addAttribute("currentPage", "inventory");

        InventoryItemEntity item = apiClient.getItem(id);
        model.addAttribute("item", item);

        // Check if the item is a user favorite
        if (principal != null) {
            List<FavoriteEntity> favorites = apiClient.getFavorites(principal.getUserId());
            List<Long> favoriteItemIds = favorites.stream()
                    .map(FavoriteEntity::getItemId)
                    .collect(Collectors.toList());
            model.addAttribute("isFavorite", favoriteItemIds.contains(id));
        } else {
            model.addAttribute("isFavorite", false);
        }

        return "inventory/detail";
    }

    @PostMapping("/inventory/{id}/favorite")
    public String toggleFavorite(@PathVariable Long id,
                                  @AuthenticationPrincipal CampusUserPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        boolean isFav = apiClient.getFavorites(principal.getUserId()).stream()
                .anyMatch(f -> f.getItemId().equals(id));
        if (isFav) {
            apiClient.removeFavorite(principal.getUserId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Removed from favorites");
        } else {
            apiClient.addFavorite(principal.getUserId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Added to favorites");
        }
        return "redirect:/inventory/" + id;
    }
}
