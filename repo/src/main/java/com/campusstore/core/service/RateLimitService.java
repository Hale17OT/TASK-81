package com.campusstore.core.service;

import com.campusstore.infrastructure.persistence.entity.RateLimitViolationEntity;
import com.campusstore.infrastructure.persistence.repository.RateLimitViolationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitViolationRepository violationRepository;

    public RateLimitService(RateLimitViolationRepository violationRepository) {
        this.violationRepository = violationRepository;
    }

    @Transactional
    public void recordViolation(String identifier, String type) {
        List<RateLimitViolationEntity> existing = violationRepository.findByIdentifier(identifier);

        if (!existing.isEmpty()) {
            RateLimitViolationEntity violation = existing.get(0);
            violation.setViolationCount(violation.getViolationCount() + 1);
            violation.setLastViolationAt(Instant.now());
            violationRepository.save(violation);
        } else {
            RateLimitViolationEntity violation = new RateLimitViolationEntity();
            violation.setIdentifier(identifier);
            violation.setIdentifierType(com.campusstore.core.domain.model.IdentifierType.valueOf(type));
            violation.setViolationCount(1);
            violation.setFirstViolationAt(Instant.now());
            violation.setLastViolationAt(Instant.now());
            violationRepository.save(violation);
        }
    }

    @Transactional(readOnly = true)
    public boolean isLockedOut(String identifier) {
        List<RateLimitViolationEntity> violations = violationRepository.findByIdentifier(identifier);
        if (violations.isEmpty()) return false;
        RateLimitViolationEntity violation = violations.get(0);
        return violation.getLockoutUntil() != null
                && violation.getLockoutUntil().isAfter(Instant.now());
    }

    @Transactional(readOnly = true)
    public int getViolationCount(String identifier) {
        List<RateLimitViolationEntity> violations = violationRepository.findByIdentifier(identifier);
        return violations.isEmpty() ? 0 : violations.get(0).getViolationCount();
    }

    @Transactional
    public void setLockoutUntil(String identifier, Instant lockoutUntil) {
        List<RateLimitViolationEntity> existing = violationRepository.findByIdentifier(identifier);
        if (!existing.isEmpty()) {
            RateLimitViolationEntity violation = existing.get(0);
            violation.setLockoutUntil(lockoutUntil);
            violationRepository.save(violation);
        }
    }
}
