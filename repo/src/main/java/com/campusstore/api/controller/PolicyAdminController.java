package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.service.AuditService;
import com.campusstore.infrastructure.persistence.entity.DataRetentionPolicyEntity;
import com.campusstore.infrastructure.persistence.repository.DataRetentionPolicyRepository;
import com.campusstore.infrastructure.security.service.CampusUserPrincipal;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only governance endpoints for {@code data_retention_policy}. Exposes the policy
 * rows the {@code AuditService.runRetentionCleanup()} job consults so admins can adjust
 * retention windows from the UI without editing the database directly. All updates are
 * audit-logged with the canonical {@code POLICY_UPDATE} action.
 *
 * Note: the audit_log policy is intentionally read-only — its 7-year minimum is hard-coded
 * in {@code AuditService} for compliance reasons. Updates to it are accepted but the
 * effective minimum is enforced server-side at cleanup time.
 */
@RestController
@RequestMapping("/api/admin/policies")
@PreAuthorize("hasRole('ADMIN')")
public class PolicyAdminController {

    private final DataRetentionPolicyRepository policyRepository;
    private final AuditService auditService;

    public PolicyAdminController(DataRetentionPolicyRepository policyRepository,
                                  AuditService auditService) {
        this.policyRepository = policyRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DataRetentionPolicyEntity>>> listPolicies() {
        return ResponseEntity.ok(ApiResponse.success(policyRepository.findAll()));
    }

    @PutMapping("/{entityType}")
    public ResponseEntity<ApiResponse<DataRetentionPolicyEntity>> updatePolicy(
            @AuthenticationPrincipal CampusUserPrincipal principal,
            @PathVariable String entityType,
            @RequestBody UpdatePolicyRequest body) {
        DataRetentionPolicyEntity policy = policyRepository.findByEntityType(entityType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DataRetentionPolicy", entityType));

        Integer previousDays = policy.getRetentionDays();
        policy.setRetentionDays(body.getRetentionDays());
        if (body.getDescription() != null) {
            policy.setDescription(body.getDescription());
        }
        DataRetentionPolicyEntity saved = policyRepository.save(policy);

        auditService.log(
                principal != null ? principal.getUserId() : null,
                "POLICY_UPDATE",
                "DataRetentionPolicy",
                saved.getId(),
                "{\"entityType\":\"" + entityType + "\""
                        + ",\"previousRetentionDays\":" + previousDays
                        + ",\"newRetentionDays\":" + saved.getRetentionDays() + "}"
        );

        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    /** Inline DTO — only retention_days and description are admin-mutable. */
    public static class UpdatePolicyRequest {
        @Min(value = 1, message = "retentionDays must be >= 1")
        private Integer retentionDays;
        private String description;

        public Integer getRetentionDays() { return retentionDays; }
        public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
