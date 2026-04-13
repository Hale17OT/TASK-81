package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.core.service.NotificationService;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationEntity>>> listNotifications(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = principal != null ? principal.getUserId() : null;
        Page<NotificationEntity> result = notificationService.listNotifications(
                userId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        notificationService.markRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
