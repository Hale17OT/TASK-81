package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateZoneRequest;
import com.campusstore.api.dto.SetZoneDistanceRequest;
import com.campusstore.core.service.ZoneService;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/zones")
@PreAuthorize("hasRole('ADMIN')")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneEntity>>> listZones() {
        List<ZoneEntity> zones = zoneService.listAll();
        return ResponseEntity.ok(ApiResponse.success(zones));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createZone(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateZoneRequest request) {
        ZoneEntity created = zoneService.create(
                request.getName(),
                request.getDescription(),
                request.getBuilding(),
                request.getFloorLevel(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @PostMapping("/distances")
    public ResponseEntity<ApiResponse<Void>> setZoneDistance(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody SetZoneDistanceRequest request) {
        zoneService.setDistance(
                request.getFromZoneId(),
                request.getToZoneId(),
                request.getWeight(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success());
    }
}
