package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateDepartmentRequest;
import com.campusstore.core.service.DepartmentService;
import com.campusstore.infrastructure.persistence.entity.DepartmentEntity;
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
@RequestMapping("/api/admin/departments")
@PreAuthorize("hasRole('ADMIN')")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentEntity>>> listDepartments() {
        List<DepartmentEntity> departments = departmentService.listAll();
        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDepartment(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentEntity created = departmentService.create(
                request.getName(),
                request.getDescription(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId(), "name", created.getName())));
    }
}
