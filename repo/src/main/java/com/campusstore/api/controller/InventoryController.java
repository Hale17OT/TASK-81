package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateItemRequest;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.api.dto.UpdateItemRequest;
import com.campusstore.core.service.InventoryService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<InventoryItemEntity>>> listItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<InventoryItemEntity> result = inventoryService.listItems(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryItemEntity>> getItem(
            @PathVariable Long id) {
        InventoryItemEntity item = inventoryService.getItem(id);
        return ResponseEntity.ok(ApiResponse.success(item));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createItem(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateItemRequest request) {
        InventoryItemEntity created = inventoryService.createItem(
                request.getName(),
                request.getDescription(),
                request.getSku(),
                request.getCategoryId(),
                request.getDepartmentId(),
                request.getLocationId(),
                request.getPriceUsd(),
                request.getQuantity(),
                request.isRequiresApproval(),
                principal != null ? principal.getUserId() : null,
                request.getCondition(),
                request.getExpirationDate()
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateItem(
            @PathVariable Long id,
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody UpdateItemRequest request) {
        inventoryService.updateItem(
                id,
                request.getName(),
                request.getDescription(),
                request.getPriceUsd(),
                request.getQuantity(),
                request.getRequiresApproval(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateItem(
            @PathVariable Long id,
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        inventoryService.deactivateItem(id, principal != null ? principal.getUserId() : null);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
