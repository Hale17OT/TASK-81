package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.NotificationPrefRequest;
import com.campusstore.core.service.NotificationService;
import com.campusstore.infrastructure.persistence.entity.NotificationPreferenceEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationService notificationService;

    public NotificationPreferenceController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationPreferenceEntity>>> getPreferences(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        List<NotificationPreferenceEntity> prefs =
                notificationService.getPreferences(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(prefs));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updatePreferences(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody NotificationPrefRequest request) {
        for (NotificationPrefRequest.NotificationPrefEntry entry : request.getPreferences()) {
            notificationService.updatePreferences(
                    principal.getUserId(),
                    entry.getType(),
                    entry.isEnabled(),
                    entry.isEmailEnabled()
            );
        }
        return ResponseEntity.ok(ApiResponse.success());
    }
}
