package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.EmailOutboxStatus;
import com.campusstore.infrastructure.persistence.entity.EmailOutboxEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntity, Long> {

    Page<EmailOutboxEntity> findByStatus(EmailOutboxStatus status, Pageable pageable);

    List<EmailOutboxEntity> findByStatusOrderByCreatedAtAsc(EmailOutboxStatus status);

    @Modifying
    @Transactional
    void deleteByCreatedAtBefore(Instant cutoff);
}
