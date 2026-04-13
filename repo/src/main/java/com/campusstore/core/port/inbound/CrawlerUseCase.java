package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.CrawlerTaskStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port for external-data crawler job operations.
 */
public interface CrawlerUseCase {

    /**
     * List all crawler jobs.
     *
     * @return list of crawler job views
     */
    List<CrawlerJobView> listJobs();

    /**
     * Create a new crawler job definition.
     *
     * @param command the job details
     * @return the id of the created job
     */
    Long createJob(CreateCrawlerJobCommand command);

    /**
     * Update an existing crawler job definition.
     *
     * @param id      the job id
     * @param command the updated job details
     */
    void updateJob(Long id, CreateCrawlerJobCommand command);

    /**
     * Trigger execution of a crawler job.
     *
     * @param jobId the job id to run
     */
    void runJob(Long jobId);

    /**
     * Get failures for a specific crawler job.
     *
     * @param jobId    the job id
     * @param pageable pagination information
     * @return a page of failure entries
     */
    Page<CrawlerFailureView> getFailures(Long jobId, Pageable pageable);

    /**
     * Check data-quality thresholds for a crawler job and return any violations.
     *
     * @param jobId the job id
     * @return the threshold check result
     */
    ThresholdCheckResult checkThresholds(Long jobId);

    // ── Command types ──────────────────────────────────────────────────

    record CreateCrawlerJobCommand(
            String name,
            String sourceUrl,
            String cronExpression,
            int failureThreshold,
            boolean enabled
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record CrawlerJobView(
            Long id,
            String name,
            String sourceUrl,
            String cronExpression,
            int failureThreshold,
            boolean enabled,
            CrawlerTaskStatus lastStatus,
            Instant lastRunAt,
            Instant createdAt
    ) {
    }

    record CrawlerFailureView(
            Long id,
            Long jobId,
            String errorMessage,
            String stackTrace,
            Instant occurredAt
    ) {
    }

    record ThresholdCheckResult(
            Long jobId,
            boolean thresholdExceeded,
            int recentFailureCount,
            int threshold,
            String message
    ) {
    }
}
