package com.campusstore.web.controller;

import com.campusstore.api.dto.PagedResponse;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationWebController {

    private final InternalApiClient apiClient;

    public NotificationWebController(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @PostMapping("/notifications/{id}/mark-read")
    public String markRead(@AuthenticationPrincipal CampusUserPrincipal principal,
                           @PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiClient.markRead(id, principal.getUserId());
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/mark-all-read")
    public String markAllRead(@AuthenticationPrincipal CampusUserPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        apiClient.markAllRead(principal.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read");
        return "redirect:/notifications";
    }

    @GetMapping("/notifications")
    public String notificationCenter(@AuthenticationPrincipal CampusUserPrincipal principal,
                                      @RequestParam(value = "page", defaultValue = "0") int page,
                                      @RequestParam(value = "size", defaultValue = "20") int size,
                                      Model model) {
        model.addAttribute("currentPage", "notifications");

        PagedResponse<NotificationEntity> notifications =
                apiClient.listNotifications(principal.getUserId(), PageRequest.of(page, size));
        model.addAttribute("notifications", notifications);

        long unreadCount = apiClient.getUnreadCount(principal.getUserId());
        model.addAttribute("unreadCount", unreadCount);

        return "notification/index";
    }
}
