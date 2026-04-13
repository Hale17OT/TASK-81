package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.core.service.CategoryService;
import com.campusstore.infrastructure.persistence.entity.CategoryEntity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only categories endpoint for any authenticated user.
 *
 * Creation/modification remains under {@code /api/admin/categories} with ADMIN gating.
 * Splitting the GET off here means the search page (and any other non-admin feature
 * that needs the category list) can consume categories over the REST contract without
 * hitting the admin firewall — closing the policy gap the audit flagged where web
 * controllers were fetching categories directly via the service layer.
 */
@RestController
@RequestMapping("/api/categories")
public class PublicCategoryController {

    private final CategoryService categoryService;

    public PublicCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryEntity>>> listCategories() {
        // Authentication (not admin role) is required by the default security rule
        // that every non-allow-listed /api/** route needs an authenticated principal.
        return ResponseEntity.ok(ApiResponse.success(categoryService.listAll()));
    }
}
