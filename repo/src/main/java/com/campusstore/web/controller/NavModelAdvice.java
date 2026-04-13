package com.campusstore.web.controller;

import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import com.campusstore.web.client.InternalApiClient;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Global model-attribute supplier for the nav bar's notification fragment
 * ({@code templates/fragments/notifications.html}).
 *
 * Every Thymeleaf view that includes the notification bell reads
 * {@code unreadNotificationCount} and {@code recentNotifications}. Without this advice
 * those attributes are {@code null} on every page, so the badge never renders and the
 * dropdown list is always empty. This @ControllerAdvice populates them for any
 * authenticated request — unauthenticated requests get safe defaults so the fragment
 * still renders cleanly.
 */
@ControllerAdvice(basePackages = "com.campusstore.web.controller")
public class NavModelAdvice {

    private static final int RECENT_LIMIT = 5;

    private final InternalApiClient apiClient;

    public NavModelAdvice(InternalApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(@AuthenticationPrincipal CampusUserPrincipal principal) {
        if (principal == null) return 0L;
        try {
            return apiClient.getUnreadCount(principal.getUserId());
        } catch (Exception e) {
            // Nav rendering must never take down a page — fall back to a safe default.
            return 0L;
        }
    }

    @ModelAttribute("recentNotifications")
    public List<NotificationEntity> recentNotifications(@AuthenticationPrincipal CampusUserPrincipal principal) {
        if (principal == null) return List.of();
        try {
            return apiClient
                    .listNotifications(principal.getUserId(), PageRequest.of(0, RECENT_LIMIT))
                    .getContent();
        } catch (Exception e) {
            return List.of();
        }
    }
}
