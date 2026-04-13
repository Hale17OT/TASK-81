package com.campusstore.infrastructure.persistence.entity;

import com.campusstore.core.domain.model.CrawlerSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "crawler_job")
public class CrawlerJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private CrawlerSourceType sourceType;

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "success_rate")
    private BigDecimal successRate;

    @Column(name = "avg_latency_ms")
    private Integer avgLatencyMs;

    @Column(name = "parse_hit_rate")
    private BigDecimal parseHitRate;

    @Column(name = "anti_bot_blocks")
    private Integer antiBotBlocks;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CrawlerSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(CrawlerSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(BigDecimal successRate) {
        this.successRate = successRate;
    }

    public Integer getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(Integer avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public BigDecimal getParseHitRate() {
        return parseHitRate;
    }

    public void setParseHitRate(BigDecimal parseHitRate) {
        this.parseHitRate = parseHitRate;
    }

    public Integer getAntiBotBlocks() {
        return antiBotBlocks;
    }

    public void setAntiBotBlocks(Integer antiBotBlocks) {
        this.antiBotBlocks = antiBotBlocks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
