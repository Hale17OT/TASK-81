package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateLocationRequest;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.api.dto.PickPathRequest;
import com.campusstore.api.dto.PutawayRequest;
import com.campusstore.api.dto.SimulationRequest;
import com.campusstore.core.service.WarehouseService;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warehouse")
@PreAuthorize("hasRole('ADMIN')")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<PagedResponse<StorageLocationEntity>>> listLocations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<StorageLocationEntity> result = warehouseService.listLocations(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @PostMapping("/locations")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createLocation(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateLocationRequest request) {
        StorageLocationEntity created = warehouseService.createLocation(
                request.getName(),
                request.getZoneId(),
                request.getX(),
                request.getY(),
                request.getLevel(),
                request.getTemperatureZone(),
                request.getSecurityLevel(),
                request.getCapacity(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @PutMapping("/locations/{id}")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @PathVariable Long id,
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateLocationRequest request) {
        warehouseService.updateLocation(
                id,
                request.getName(),
                request.getX(),
                request.getY(),
                request.getLevel(),
                request.getCapacity(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/putaway")
    public ResponseEntity<ApiResponse<List<StorageLocationEntity>>> recommendPutaway(
            @Valid @RequestBody PutawayRequest request) {
        List<StorageLocationEntity> result = warehouseService.recommendPutaway(request.getItemId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/pick-path")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generatePickPath(
            @Valid @RequestBody PickPathRequest request) {
        Map<String, Object> result = warehouseService.generatePickPath(request.getPickTaskIds());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runSimulation(
            @Valid @RequestBody SimulationRequest request) {
        double multiplier = request.getProposedLevelMultiplier() != null
                ? request.getProposedLevelMultiplier()
                : 2.0; // service-level default matches active PathCostConfig baseline

        // Build a matrix-aware config payload. Omit keys that were not supplied so the
        // service falls back to the active PathCostConfig values.
        StringBuilder cfg = new StringBuilder("{\"levelMultiplier\":").append(multiplier);
        if (request.getHorizontalWeight() != null) {
            cfg.append(",\"horizontalWeight\":").append(request.getHorizontalWeight());
        }
        if (request.getZoneTransitionWeight() != null) {
            cfg.append(",\"zoneTransitionWeight\":").append(request.getZoneTransitionWeight());
        }
        cfg.append('}');

        Map<String, Object> result = warehouseService.runSimulation(cfg.toString());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
