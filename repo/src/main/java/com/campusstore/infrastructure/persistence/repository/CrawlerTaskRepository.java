package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.CrawlerTaskStatus;
import com.campusstore.infrastructure.persistence.entity.CrawlerTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CrawlerTaskRepository extends JpaRepository<CrawlerTaskEntity, Long> {

    Page<CrawlerTaskEntity> findByJobId(Long jobId, Pageable pageable);

    List<CrawlerTaskEntity> findByJobIdAndStatus(Long jobId, CrawlerTaskStatus status);

    Page<CrawlerTaskEntity> findByJobIdAndStatus(Long jobId, CrawlerTaskStatus status, Pageable pageable);

    @Query("SELECT ct.jobId, COUNT(ct) FROM CrawlerTaskEntity ct WHERE ct.status = 'FAILED' GROUP BY ct.jobId")
    List<Object[]> countFailedTasksPerJob();

    @Query("SELECT COUNT(ct) * 1.0 / NULLIF((SELECT COUNT(ct2) FROM CrawlerTaskEntity ct2 WHERE ct2.jobId = :jobId AND ct2.createdAt >= :since), 0) FROM CrawlerTaskEntity ct WHERE ct.jobId = :jobId AND ct.status = 'SUCCESS' AND ct.createdAt >= :since")
    Double findSuccessRateByJobIdSince(@Param("jobId") Long jobId, @Param("since") Instant since);

    @Query(value = "SELECT latency_ms FROM crawler_task WHERE job_id = :jobId AND created_at >= :since AND latency_ms IS NOT NULL ORDER BY latency_ms ASC LIMIT 1 OFFSET (SELECT FLOOR(0.95 * COUNT(*)) FROM crawler_task WHERE job_id = :jobId AND created_at >= :since AND latency_ms IS NOT NULL)",
           nativeQuery = true)
    Integer findP95LatencyByJobIdSince(@Param("jobId") Long jobId, @Param("since") Instant since);
}
