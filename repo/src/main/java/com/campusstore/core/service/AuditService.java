package com.campusstore.core.service;

import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.infrastructure.persistence.entity.AuditLogEntity;
import com.campusstore.infrastructure.persistence.entity.DataRetentionPolicyEntity;
import com.campusstore.infrastructure.persistence.entity.DeletedUserPlaceholderEntity;
import com.campusstore.infrastructure.persistence.entity.DisputeEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.AuditLogRepository;
import com.campusstore.infrastructure.persistence.repository.BrowsingHistoryRepository;
import com.campusstore.infrastructure.persistence.repository.DataRetentionPolicyRepository;
import com.campusstore.infrastructure.persistence.repository.DeletedUserPlaceholderRepository;
import com.campusstore.infrastructure.persistence.repository.DisputeRepository;
import com.campusstore.infrastructure.persistence.repository.EmailOutboxRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationRepository;
import com.campusstore.infrastructure.persistence.repository.SearchLogRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    // Dedicated audit logger that routes to the audit.log appender via logback config
    private static final Logger auditLog = LoggerFactory.getLogger("com.campusstore.audit");
    private static final int BATCH_SIZE = 1000;

    // Patterns for PII scrubbing in JSON details
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(\"(?:email|emailAddress|mail)\"\\s*:\\s*\")[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\"(?:phone|phoneNumber|mobile|telephone)\"\\s*:\\s*\")[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(\"(?:password|passwordHash|secret|token)\"\\s*:\\s*\")[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "(\"(?:ssn|socialSecurity)\"\\s*:\\s*\")[^\"]*\"", Pattern.CASE_INSENSITIVE);

    private final AuditLogRepository auditLogRepository;
    private final DataRetentionPolicyRepository dataRetentionPolicyRepository;
    private final DeletedUserPlaceholderRepository deletedUserPlaceholderRepository;
    private final UserRepository userRepository;
    private final DisputeRepository disputeRepository;
    private final SearchLogRepository searchLogRepository;
    private final BrowsingHistoryRepository browsingHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final EmailOutboxRepository emailOutboxRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        DataRetentionPolicyRepository dataRetentionPolicyRepository,
                        DeletedUserPlaceholderRepository deletedUserPlaceholderRepository,
                        UserRepository userRepository,
                        DisputeRepository disputeRepository,
                        SearchLogRepository searchLogRepository,
                        BrowsingHistoryRepository browsingHistoryRepository,
                        NotificationRepository notificationRepository,
                        EmailOutboxRepository emailOutboxRepository) {
        this.auditLogRepository = auditLogRepository;
        this.dataRetentionPolicyRepository = dataRetentionPolicyRepository;
        this.deletedUserPlaceholderRepository = deletedUserPlaceholderRepository;
        this.userRepository = userRepository;
        this.disputeRepository = disputeRepository;
        this.searchLogRepository = searchLogRepository;
        this.browsingHistoryRepository = browsingHistoryRepository;
        this.notificationRepository = notificationRepository;
        this.emailOutboxRepository = emailOutboxRepository;
    }

    public AuditLogEntity log(Long actorUserId, String action, String entityType, Long entityId, String detailsJson) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetailsJson(scrubPii(detailsJson));
        entry.setCreatedAt(Instant.now());

        AuditLogEntity saved = auditLogRepository.save(entry);
        // Route to dedicated audit log appender for forensic observability
        auditLog.info("AUDIT action={} entityType={} entityId={} actor={}", action, entityType, entityId, actorUserId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogEntity> query(String action, String entityType, Long entityId,
                                       Long actorUserId, Instant from, Instant to, Pageable pageable) {
        if (action != null) {
            return auditLogRepository.findByAction(action, pageable);
        }
        if (entityType != null && entityId != null) {
            return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        }
        if (actorUserId != null) {
            return auditLogRepository.findByActorUserId(actorUserId, pageable);
        }
        if (from != null && to != null) {
            return auditLogRepository.findByCreatedAtBetween(from, to, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    public void runRetentionCleanup() {
        log.info("Starting data retention cleanup");
        List<DataRetentionPolicyEntity> policies = dataRetentionPolicyRepository.findAll();

        for (DataRetentionPolicyEntity policy : policies) {
            String entityType = policy.getEntityType();
            int retentionDays = policy.getRetentionDays();

            // Audit log has special 7-year retention (2555 days)
            if ("audit_log".equalsIgnoreCase(entityType)) {
                retentionDays = Math.max(retentionDays, 7 * 365);
            }

            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

            try {
                switch (entityType.toLowerCase()) {
                    case "search_log" -> {
                        searchLogRepository.deleteBySearchedAtBefore(cutoff);
                        log.info("Retention cleanup for search_log: deleted records older than {} days", retentionDays);
                    }
                    case "browsing_history" -> {
                        browsingHistoryRepository.deleteByViewedAtBefore(cutoff);
                        log.info("Retention cleanup for browsing_history: deleted records older than {} days", retentionDays);
                    }
                    case "notification" -> {
                        notificationRepository.deleteByCreatedAtBefore(cutoff);
                        log.info("Retention cleanup for notification: deleted records older than {} days", retentionDays);
                    }
                    case "email_outbox" -> {
                        emailOutboxRepository.deleteByCreatedAtBefore(cutoff);
                        log.info("Retention cleanup for email_outbox: deleted records older than {} days", retentionDays);
                    }
                    case "audit_log" -> {
                        // Audit logs are IMMUTABLE — never deleted. 7-year minimum retention.
                        // Retention policy entry exists for configuration tracking only.
                        log.info("Audit log retention check: records preserved (immutable, {} day policy)", retentionDays);
                    }
                    default -> log.warn("Unknown entity type in retention policy: {}", entityType);
                }
            } catch (Exception e) {
                log.error("Failed retention cleanup for {}: {}", entityType, e.getMessage());
            }

            policy.setLastCleanupAt(Instant.now());
            dataRetentionPolicyRepository.save(policy);
        }

        log.info("Data retention cleanup completed");
    }

    public void runUserDeletion() {
        log.info("Starting cryptographic erasure for eligible users");
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        List<UserEntity> eligibleUsers = userRepository.findUsersEligibleForDeletion(cutoff);

        int deletedCount = 0;
        for (UserEntity user : eligibleUsers) {
            // Skip if user has open dispute
            boolean hasOpenDispute = disputeRepository.existsByUserIdAndStatus(user.getId(), com.campusstore.core.domain.model.DisputeStatus.OPEN);
            if (hasOpenDispute) {
                log.info("Skipping user id={} deletion: open dispute exists", user.getId());
                continue;
            }

            try {
                // Create placeholder with SHA-256 hash of username
                String hashedIdentity = sha256Hash(user.getUsername());
                DeletedUserPlaceholderEntity placeholder = new DeletedUserPlaceholderEntity();
                placeholder.setOriginalUserId(user.getId());
                placeholder.setHashedIdentity(hashedIdentity);
                placeholder.setDeletedAt(Instant.now());
                DeletedUserPlaceholderEntity savedPlaceholder = deletedUserPlaceholderRepository.save(placeholder);

                // Immutable audit: do NOT modify existing audit records.
                // Instead, create a linked erasure-mapping event that records the
                // actor_user_id → placeholder_id mapping. Queries can JOIN on this
                // to resolve deleted user references without mutating history.
                long affectedEntries = auditLogRepository.findByActorUserId(
                        user.getId(), PageRequest.of(0, 1)).getTotalElements();

                AuditLogEntity mappingEvent = new AuditLogEntity();
                mappingEvent.setActorPlaceholderId(savedPlaceholder.getId());
                mappingEvent.setActorUsernameHash(hashedIdentity);
                mappingEvent.setAction("ERASURE_MAPPING");
                mappingEvent.setEntityType("User");
                mappingEvent.setEntityId(user.getId());
                mappingEvent.setDetailsJson("{\"originalUserId\":" + user.getId()
                        + ",\"placeholderId\":" + savedPlaceholder.getId()
                        + ",\"hashedIdentity\":\"" + hashedIdentity + "\""
                        + ",\"affectedAuditEntries\":" + affectedEntries + "}");
                mappingEvent.setCreatedAt(Instant.now());
                auditLogRepository.save(mappingEvent);

                // Anonymize closed disputes (RESOLVED/DISMISSED) by linking to the placeholder
                // and nulling the user FK. Without this the dispute FK would block the hard
                // delete for any user who ever had a dispute, breaking the documented
                // "delete after 30 days unless OPEN dispute" rule. Open disputes are already
                // filtered above so only closed rows are rewritten here.
                int anonymized = disputeRepository.anonymizeForUser(user.getId(), savedPlaceholder.getId());
                if (anonymized > 0) {
                    log.info("Anonymized {} closed dispute(s) for user id={} → placeholder id={}",
                            anonymized, user.getId(), savedPlaceholder.getId());
                }

                // Hard-delete the user (cascades related data; nullable FKs use ON DELETE SET NULL)
                userRepository.delete(user);
                deletedCount++;

                // Log the erasure as an audit entry
                log.info("Cryptographic erasure completed for user id={}", user.getId());
                AuditLogEntity erasureLog = new AuditLogEntity();
                erasureLog.setActorPlaceholderId(savedPlaceholder.getId());
                erasureLog.setActorUsernameHash(hashedIdentity);
                erasureLog.setAction("CRYPTOGRAPHIC_ERASURE");
                erasureLog.setEntityType("User");
                erasureLog.setEntityId(savedPlaceholder.getOriginalUserId());
                erasureLog.setDetailsJson("{\"placeholderId\":" + savedPlaceholder.getId() + "}");
                erasureLog.setCreatedAt(Instant.now());
                auditLogRepository.save(erasureLog);

            } catch (Exception e) {
                log.error("Failed to delete user id={}", user.getId(), e);
            }
        }

        log.info("Cryptographic erasure completed: {} users deleted", deletedCount);
    }

    String scrubPii(String detailsJson) {
        if (detailsJson == null) {
            return null;
        }
        String scrubbed = detailsJson;
        scrubbed = EMAIL_PATTERN.matcher(scrubbed).replaceAll("$1[REDACTED]\"");
        scrubbed = PHONE_PATTERN.matcher(scrubbed).replaceAll("$1[REDACTED]\"");
        scrubbed = PASSWORD_PATTERN.matcher(scrubbed).replaceAll("$1[REDACTED]\"");
        scrubbed = SSN_PATTERN.matcher(scrubbed).replaceAll("$1[REDACTED]\"");
        return scrubbed;
    }

    private String sha256Hash(String input) {
        Objects.requireNonNull(input, "Input for hashing must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
