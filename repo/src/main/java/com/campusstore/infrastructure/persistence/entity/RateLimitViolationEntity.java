package com.campusstore.infrastructure.persistence.entity;

import com.campusstore.core.domain.model.IdentifierType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rate_limit_violation")
public class RateLimitViolationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identifier", nullable = false)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false)
    private IdentifierType identifierType;

    @Column(name = "violation_count")
    private Integer violationCount;

    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    @Column(name = "first_violation_at")
    private Instant firstViolationAt;

    @Column(name = "last_violation_at")
    private Instant lastViolationAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public IdentifierType getIdentifierType() {
        return identifierType;
    }

    public void setIdentifierType(IdentifierType identifierType) {
        this.identifierType = identifierType;
    }

    public Integer getViolationCount() {
        return violationCount;
    }

    public void setViolationCount(Integer violationCount) {
        this.violationCount = violationCount;
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }

    public Instant getFirstViolationAt() {
        return firstViolationAt;
    }

    public void setFirstViolationAt(Instant firstViolationAt) {
        this.firstViolationAt = firstViolationAt;
    }

    public Instant getLastViolationAt() {
        return lastViolationAt;
    }

    public void setLastViolationAt(Instant lastViolationAt) {
        this.lastViolationAt = lastViolationAt;
    }
}
