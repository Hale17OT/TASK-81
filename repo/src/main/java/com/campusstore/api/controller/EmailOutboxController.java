package com.campusstore.api.controller;

import com.campusstore.api.dto.ApiResponse;
import com.campusstore.api.dto.PagedResponse;
import com.campusstore.core.service.EmailOutboxService;
import com.campusstore.infrastructure.persistence.entity.EmailOutboxEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/email-outbox")
@PreAuthorize("hasRole('ADMIN')")
public class EmailOutboxController {

    private final EmailOutboxService emailOutboxService;

    public EmailOutboxController(EmailOutboxService emailOutboxService) {
        this.emailOutboxService = emailOutboxService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EmailOutboxEntity>>> listEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<EmailOutboxEntity> result = emailOutboxService.list(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(result)));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportToZip() {
        byte[] zipBytes = emailOutboxService.exportToZip();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "email-outbox-export.zip");
        headers.setContentLength(zipBytes.length);
        return ResponseEntity.ok()
                .headers(headers)
                .body(zipBytes);
    }
}
