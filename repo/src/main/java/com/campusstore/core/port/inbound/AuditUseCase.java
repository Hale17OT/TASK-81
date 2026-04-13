package com.campusstore.core.port.inbound;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Inbound port for audit-logging and compliance operations.
 */
public interface AuditUseCase {

    /**
     * Log an audit event.
     *
     * @param action      the action performed (e.g. "CREATE", "UPDATE", "DELETE")
     * @param entityType  the entity type (e.g. "USER", "ITEM", "REQUEST")
     * @param entityId    the entity id
     * @param detailsJson additional details as a JSON string
     * @param actorUserId the id of the user who performed the action
     * @param ipAddress   the IP address of the actor
     */
    void log(String action, String entityType, Long entityId, String detailsJson,
             Long actorUserId, String ipAddress);

    /**
     * Query audit log entries with filters and pagination.
     *
     * @param query    the audit query filters
     * @param pageable pagination information
     * @return a page of audit entry views
     */
    Page<AuditEntryView> query(AuditQuery query, Pageable pageable);

    /**
     * Run retention cleanup to remove audit entries beyond the retention period.
     */
    void runRetentionCleanup();

    /**
     * Run user-deletion workflow (anonymize/purge user data per GDPR / policy).
     */
    void runUserDeletion();

    // ── Query types ────────────────────────────────────────────────────

    record AuditQuery(
            String action,
            String entityType,
            LocalDateTime from,
            LocalDateTime to,
            Long actorUserId
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record AuditEntryView(
            Long id,
            String action,
            String entityType,
            Long entityId,
            String detailsJson,
            Long actorUserId,
            String actorUsername,
            String ipAddress,
            Instant createdAt
    ) {
    }
}
