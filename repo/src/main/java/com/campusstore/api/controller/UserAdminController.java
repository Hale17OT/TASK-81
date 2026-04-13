package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.ChangeRolesRequest;
import com.campusstore.api.dto.ChangeStatusRequest;
import com.campusstore.api.dto.CreateUserRequest;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.api.dto.UpdateUserRequest;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.UserManagementService;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
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
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserManagementService userManagementService;

    public UserAdminController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserEntity>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserEntity> result = userManagementService.listUsers(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createUser(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateUserRequest request) {
        Role initialRole = (request.getRoles() != null && !request.getRoles().isEmpty())
                ? request.getRoles().get(0) : null;
        UserEntity created = userManagementService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getDisplayName(),
                request.getEmail(),
                request.getPhone(),
                initialRole,
                request.getDepartmentId(),
                principal != null ? principal.getUserId() : null
        );
        // Assign additional roles if more than one
        if (request.getRoles() != null && request.getRoles().size() > 1) {
            for (int i = 1; i < request.getRoles().size(); i++) {
                userManagementService.assignRole(
                        created.getId(),
                        request.getRoles().get(i),
                        principal != null ? principal.getUserId() : null
                );
            }
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserEntity>> getUser(
            @PathVariable Long id) {
        UserEntity user = userManagementService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {
        userManagementService.updateUser(
                id,
                request.getDisplayName(),
                request.getEmail(),
                request.getPhone(),
                request.getDepartmentId(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> changeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody ChangeStatusRequest request) {
        userManagementService.changeAccountStatus(id, request.getStatus(),
                principal != null ? principal.getUserId() : null);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<Void>> changeRoles(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRolesRequest request,
            @AuthenticationPrincipal CampusUserPrincipal principal) {
        // Get the user to see their current roles
        UserEntity user = userManagementService.getUserById(id);
        List<Role> desiredRoles = request.getRoles();

        // We need to look at existing roles - get the user's roles from the entity
        List<Role> currentRoles = user.getRoles() != null
                ? user.getRoles().stream().map(UserRoleEntity::getRole).toList()
                : List.of();

        // Revoke roles not in the desired list
        for (Role role : currentRoles) {
            if (!desiredRoles.contains(role)) {
                userManagementService.revokeRole(id, role,
                        principal != null ? principal.getUserId() : null);
            }
        }

        // Assign roles not currently held
        for (Role role : desiredRoles) {
            if (!currentRoles.contains(role)) {
                userManagementService.assignRole(id, role,
                        principal != null ? principal.getUserId() : null);
            }
        }

        return ResponseEntity.ok(ApiResponse.success());
    }
}
