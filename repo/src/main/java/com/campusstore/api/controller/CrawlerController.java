package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.CreateCrawlerJobRequest;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.core.service.CrawlerService;
import com.campusstore.infrastructure.persistence.entity.CrawlerJobEntity;
import com.campusstore.infrastructure.persistence.entity.CrawlerTaskEntity;
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
@RequestMapping("/api/admin/crawler")
@PreAuthorize("hasRole('ADMIN')")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<CrawlerJobEntity>>> listJobs() {
        List<CrawlerJobEntity> jobs = crawlerService.listJobs();
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createJob(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateCrawlerJobRequest request) {
        CrawlerJobEntity created = crawlerService.createJob(
                request.getName(),
                request.getSourceType() != null ? request.getSourceType() : "INTRANET_PAGE",
                request.getSourcePath(),
                request.getCronExpression(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", created.getId())));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<Void>> updateJob(
            @PathVariable Long id,
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @Valid @RequestBody CreateCrawlerJobRequest request) {
        crawlerService.updateJob(
                id,
                request.getName(),
                request.getSourceType() != null ? request.getSourceType() : "INTRANET_PAGE",
                request.getSourcePath(),
                request.getCronExpression(),
                request.isEnabled(),
                principal != null ? principal.getUserId() : null
        );
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/jobs/{id}/run")
    public ResponseEntity<ApiResponse<Void>> runJob(@PathVariable Long id) {
        crawlerService.runJob(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/jobs/{id}/failures")
    public ResponseEntity<ApiResponse<PagedResponse<CrawlerTaskEntity>>> getFailures(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CrawlerTaskEntity> result = crawlerService.getFailures(
                id, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }
}
