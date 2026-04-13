package com.campusstore.core.port.inbound;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Inbound port for email outbox operations.
 */
public interface EmailOutboxUseCase {

    /**
     * List outbox entries with pagination.
     *
     * @param pageable pagination information
     * @return a page of outbox entry views
     */
    Page<OutboxEntryView> listEntries(Pageable pageable);

    /**
     * Export outbox entries as a ZIP archive.
     *
     * @return the ZIP file bytes
     */
    byte[] exportToZip();

    // ── View types ─────────────────────────────────────────────────────

    record OutboxEntryView(
            Long id,
            Long recipientUserId,
            String subject,
            String bodyText,
            String status,
            Instant createdAt,
            Instant exportedAt
    ) {
    }
}
