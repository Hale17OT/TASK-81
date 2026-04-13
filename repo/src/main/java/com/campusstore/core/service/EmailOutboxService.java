package com.campusstore.core.service;

import com.campusstore.core.domain.model.EmailOutboxStatus;
import com.campusstore.infrastructure.persistence.entity.EmailOutboxEntity;
import com.campusstore.infrastructure.persistence.repository.EmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EmailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository emailOutboxRepository;

    public EmailOutboxService(EmailOutboxRepository emailOutboxRepository) {
        this.emailOutboxRepository = emailOutboxRepository;
    }

    @Transactional(readOnly = true)
    public Page<EmailOutboxEntity> list(Pageable pageable) {
        return emailOutboxRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<EmailOutboxEntity> listByStatus(EmailOutboxStatus status, Pageable pageable) {
        return emailOutboxRepository.findByStatus(status, pageable);
    }

    @Transactional
    public byte[] exportToZip() {
        List<EmailOutboxEntity> queued = emailOutboxRepository
                .findByStatusOrderByCreatedAtAsc(EmailOutboxStatus.QUEUED);

        if (queued.isEmpty()) {
            log.info("Email outbox export: no queued messages");
            return createEmptyZip();
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                int index = 1;
                for (EmailOutboxEntity email : queued) {
                    String filename = String.format("email_%04d_%s.eml", index, email.getId());
                    String emlContent = buildEmlContent(email);

                    ZipEntry entry = new ZipEntry(filename);
                    zos.putNextEntry(entry);
                    zos.write(emlContent.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    email.setStatus(EmailOutboxStatus.EXPORTED);
                    email.setExportedAt(Instant.now());
                    emailOutboxRepository.save(email);

                    index++;
                }
            }

            log.info("Email outbox exported: {} messages", queued.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export email outbox", e);
            throw new RuntimeException("Failed to export email outbox", e);
        }
    }

    private String buildEmlContent(EmailOutboxEntity email) {
        if (email.getEmlData() != null && !email.getEmlData().isBlank()) {
            return email.getEmlData();
        }

        StringBuilder eml = new StringBuilder();
        eml.append("MIME-Version: 1.0\r\n");
        eml.append("From: campusstore@localhost\r\n");
        eml.append("To: recipient@campus.edu\r\n");
        eml.append("Subject: ").append(email.getSubject() != null ? email.getSubject() : "(no subject)").append("\r\n");
        eml.append("Date: ").append(email.getCreatedAt() != null ? email.getCreatedAt().toString() : Instant.now().toString()).append("\r\n");
        eml.append("Content-Type: text/plain; charset=UTF-8\r\n");
        eml.append("\r\n");
        eml.append(email.getBodyText() != null ? email.getBodyText() : "");

        return eml.toString();
    }

    private byte[] createEmptyZip() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry entry = new ZipEntry("README.txt");
                zos.putNextEntry(entry);
                zos.write("No queued emails to export.".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty ZIP", e);
        }
    }
}
