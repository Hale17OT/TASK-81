package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateCategoryRequest;
import com.campusstore.core.service.CategoryService;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;
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
@RequestMapping("/api/admin/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Legacy admin-scoped list. Retained for back-compat with any tooling that relies on
     * the {@code /api/admin/categories} path. Non-admin callers should use
     * {@link PublicCategoryController} at {@code /api/categories}.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CategoryEntity>>> listCategories() {
        List<CategoryEntity> categories = categoryService.listAll();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createCategory(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryEntity created = categoryService.create(
                request.getName(),
                request.getDescription(),
                request.getParentId(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }
}
