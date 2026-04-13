package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findByAction(String action, Pageable pageable);

    Page<AuditLogEntity> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

    Page<AuditLogEntity> findByActorUserId(Long actorUserId, Pageable pageable);

    Page<AuditLogEntity> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    Page<AuditLogEntity> findAll(Pageable pageable);

    // NOTE: Audit logs are immutable per governance requirements.
    // No delete methods are provided. Retention is enforced at 7 years minimum.
    // The cryptographic erasure process creates linked erasure-event records
    // rather than modifying existing audit entries.
}
