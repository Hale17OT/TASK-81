package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.RateLimitViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RateLimitViolationRepository extends JpaRepository<RateLimitViolationEntity, Long> {

    List<RateLimitViolationEntity> findByIdentifier(String identifier);

    List<RateLimitViolationEntity> findByIdentifierAndLockoutUntilAfter(String identifier, Instant now);
}
