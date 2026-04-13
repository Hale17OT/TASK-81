package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateRequestDTO;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.api.dto.RejectRequestDTO;
import com.campusstore.core.service.RequestService;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
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

import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createRequest(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateRequestDTO request) {
        Long userId = principal != null ? principal.getUserId() : null;
        ItemRequestEntity created = requestService.createRequest(
                userId,
                request.getItemId(),
                request.getQuantity(),
                request.getJustification()
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PagedResponse<ItemRequestEntity>>> listMyRequests(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = principal != null ? principal.getUserId() : null;
        Page<ItemRequestEntity> result = requestService.listMyRequests(
                userId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<ItemRequestEntity>>> listPendingApprovals(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = principal != null ? principal.getUserId() : null;
        Page<ItemRequestEntity> result = requestService.listPendingApprovals(
                userId, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemRequestEntity>> getRequest(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        ItemRequestEntity request = requestService.getRequest(id, userId);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveRequest(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        requestService.approveRequest(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectRequest(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequestDTO request) {
        Long userId = principal != null ? principal.getUserId() : null;
        requestService.rejectRequest(id, userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        requestService.cancelRequest(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/picked-up")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markPickedUp(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        requestService.markPickedUp(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/start-picking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> startPicking(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        requestService.startPicking(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/ready-for-pickup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markReadyForPickup(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable Long id) {
        Long userId = principal != null ? principal.getUserId() : null;
        requestService.markReadyForPickup(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
