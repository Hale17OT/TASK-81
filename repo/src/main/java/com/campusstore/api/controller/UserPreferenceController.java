package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.DndRequest;
import com.campusstore.api.dto.PersonalizationRequest;
import com.campusstore.core.service.ProfileService;
import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/preferences")
public class UserPreferenceController {

    private final ProfileService profileService;

    public UserPreferenceController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserPreferenceEntity>> getPreferences(
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        UserPreferenceEntity prefs = profileService.getPreferences(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(prefs));
    }

    @PutMapping("/dnd")
    public ResponseEntity<ApiResponse<Void>> setDnd(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody DndRequest request) {
        profileService.updateDnd(principal.getUserId(), request.getStartTime(), request.getEndTime());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/personalization")
    public ResponseEntity<ApiResponse<Void>> togglePersonalization(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody PersonalizationRequest request) {
        profileService.togglePersonalization(principal.getUserId(), request.isEnabled());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
