package com.campusstore.unit.service;

import com.campusstore.core.domain.event.BusinessException;
import com.campusstore.core.domain.model.CrawlerSourceType;
import com.campusstore.core.domain.model.CrawlerTaskStatus;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.CrawlerService;
import com.campusstore.core.service.NotificationService;
import com.campusstore.infrastructure.config.AppProperties;
import com.campusstore.infrastructure.persistence.entity.CrawlerJobEntity;
import com.campusstore.infrastructure.persistence.entity.CrawlerTaskEntity;
import com.campusstore.infrastructure.persistence.repository.CrawlerJobRepository;
import com.campusstore.infrastructure.persistence.repository.CrawlerTaskRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CrawlerService} covering offline-source validation,
 * anti-bot content detection, and task outcome branches in runJob().
 * Lenient stubbing is used because many setup stubs are conditionally needed
 * depending on which code path the validation triggers.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrawlerServiceTest {

    @Mock private CrawlerJobRepository crawlerJobRepository;
    @Mock private CrawlerTaskRepository crawlerTaskRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;
    @Mock private AppProperties appProperties;
    @Mock private AppProperties.Crawler crawlerProps;

    @InjectMocks
    private CrawlerService crawlerService;

    // ── createJob — source validation branches ────────────────────────

    @Test
    void createJob_invalidSourceType_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "UNKNOWN_TYPE", "/some/path", null, 1L));
        verify(crawlerJobRepository, never()).save(any());
    }

    @Test
    void createJob_fileSourceWithHttpUrl_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "FILE", "http://external.com/file", null, 1L));
        assertTrue(ex.getMessage().contains("local filesystem path"),
                "Error must explain FILE requires a local path");
        verify(crawlerJobRepository, never()).save(any());
    }

    @Test
    void createJob_fileSourceWithHttpsUrl_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "FILE", "https://evil.com/data.csv", null, 1L));
    }

    @Test
    void createJob_fileSourceWithLocalPath_createsJob() {
        CrawlerJobEntity saved = new CrawlerJobEntity();
        saved.setId(1L);
        when(crawlerJobRepository.save(any())).thenReturn(saved);

        CrawlerJobEntity result = crawlerService.createJob("Job", "FILE", "/data/feed.html", null, 1L);

        assertNotNull(result);
        verify(crawlerJobRepository).save(any());
    }

    @Test
    void createJob_intranetSourceWithExternalHost_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "INTRANET_PAGE", "http://google.com/page", null, 1L));
        verify(crawlerJobRepository, never()).save(any());
    }

    @Test
    void createJob_intranetSourceWithLocalhostUrl_createsJob() {
        CrawlerJobEntity saved = new CrawlerJobEntity();
        saved.setId(2L);
        when(crawlerJobRepository.save(any())).thenReturn(saved);

        CrawlerJobEntity result = crawlerService.createJob(
                "Job", "INTRANET_PAGE", "http://localhost:8080/data", null, 1L);

        assertNotNull(result);
    }

    @Test
    void createJob_intranetSourceWithInternalDomain_createsJob() {
        // wiki.internal fails DNS but matches the .internal suffix heuristic → allowed
        CrawlerJobEntity saved = new CrawlerJobEntity();
        saved.setId(3L);
        when(crawlerJobRepository.save(any())).thenReturn(saved);

        CrawlerJobEntity result = crawlerService.createJob(
                "Job", "INTRANET_PAGE", "http://wiki.internal/index.html", null, 1L);

        assertNotNull(result);
    }

    @Test
    void createJob_intranetSourceWithBlankPath_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "INTRANET_PAGE", "   ", null, 1L));
    }

    @Test
    void createJob_intranetSourceWithInvalidUrl_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "INTRANET_PAGE", "not-a-url", null, 1L));
    }

    @Test
    void createJob_intranetSourceWithNonHttpScheme_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                crawlerService.createJob("Job", "INTRANET_PAGE", "ftp://intranet.local/file", null, 1L));
    }

    // ── updateJob — null-field branches ──────────────────────────────

    @Test
    void updateJob_nullFields_doesNotOverwriteExistingValues() {
        CrawlerJobEntity existing = new CrawlerJobEntity();
        existing.setId(10L);
        existing.setName("Original");
        existing.setSourceType(CrawlerSourceType.FILE);
        existing.setSourcePath("/data/feed.html");
        existing.setIsActive(true);
        when(crawlerJobRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(crawlerJobRepository.save(any())).thenReturn(existing);

        crawlerService.updateJob(10L, null, null, null, null, null, 1L);

        assertEquals("Original", existing.getName());
        assertEquals(CrawlerSourceType.FILE, existing.getSourceType());
        assertTrue(existing.getIsActive());
    }

    @Test
    void updateJob_isActiveFalse_deactivatesJob() {
        CrawlerJobEntity existing = new CrawlerJobEntity();
        existing.setId(11L);
        existing.setSourceType(CrawlerSourceType.FILE);
        existing.setIsActive(true);
        when(crawlerJobRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(crawlerJobRepository.save(any())).thenReturn(existing);

        crawlerService.updateJob(11L, null, null, null, null, false, 1L);

        assertFalse(existing.getIsActive());
    }

    // ── runJob — file content branches ────────────────────────────────

    @Test
    void runJob_fileWithNormalContent_recordsSuccessTask(@TempDir Path tmpDir) throws IOException {
        Path file = tmpDir.resolve("page.html");
        Files.writeString(file, "<html><body><h1>Welcome to CampusStore Intranet</h1></body></html>");

        CrawlerJobEntity job = buildFileJob(20L, file.toString());
        when(crawlerJobRepository.findById(20L)).thenReturn(Optional.of(job));
        when(crawlerJobRepository.save(any())).thenReturn(job);
        when(crawlerTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.SUCCESS)))
                .thenReturn(List.of());
        when(crawlerProps.getSnapshotCap()).thenReturn(50);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);

        CrawlerTaskEntity result = crawlerService.runJob(20L);

        assertEquals(CrawlerTaskStatus.SUCCESS, result.getStatus());
        assertEquals(0, job.getAntiBotBlocks());
    }

    @Test
    void runJob_fileWithAntiBotContent_recordsFailedAndIncrementsBlock(@TempDir Path tmpDir)
            throws IOException {
        Path file = tmpDir.resolve("blocked.html");
        Files.writeString(file, "<html><body>Access Denied — complete the captcha to continue</body></html>");

        CrawlerJobEntity job = buildFileJob(21L, file.toString());
        job.setAntiBotBlocks(0);
        when(crawlerJobRepository.findById(21L)).thenReturn(Optional.of(job));
        when(crawlerJobRepository.save(any())).thenReturn(job);
        when(crawlerTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.FAILED)))
                .thenReturn(List.of());
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.SUCCESS)))
                .thenReturn(List.of());
        when(crawlerProps.getSnapshotCap()).thenReturn(50);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);

        CrawlerTaskEntity result = crawlerService.runJob(21L);

        assertEquals(CrawlerTaskStatus.FAILED, result.getStatus());
        assertEquals(1, job.getAntiBotBlocks());
    }

    @Test
    void runJob_nonexistentFile_recordsFailedTask() {
        CrawlerJobEntity job = buildFileJob(22L, "/nonexistent/path/file.html");
        when(crawlerJobRepository.findById(22L)).thenReturn(Optional.of(job));
        when(crawlerJobRepository.save(any())).thenReturn(job);
        when(crawlerTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.FAILED)))
                .thenReturn(List.of());
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.SUCCESS)))
                .thenReturn(List.of());
        when(crawlerProps.getSnapshotCap()).thenReturn(50);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);

        CrawlerTaskEntity result = crawlerService.runJob(22L);

        assertEquals(CrawlerTaskStatus.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void runJob_fileWithRateLimitedContent_detectsAntiBot(@TempDir Path tmpDir)
            throws IOException {
        Path file = tmpDir.resolve("ratelimit.html");
        Files.writeString(file, "<html><body>You have been rate limited. Please try again later.</body></html>");

        CrawlerJobEntity job = buildFileJob(23L, file.toString());
        job.setAntiBotBlocks(1);
        when(crawlerJobRepository.findById(23L)).thenReturn(Optional.of(job));
        when(crawlerJobRepository.save(any())).thenReturn(job);
        when(crawlerTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.FAILED)))
                .thenReturn(List.of());
        when(crawlerTaskRepository.findByJobIdAndStatus(any(Long.class), eq(CrawlerTaskStatus.SUCCESS)))
                .thenReturn(List.of());
        when(crawlerProps.getSnapshotCap()).thenReturn(50);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);

        CrawlerTaskEntity result = crawlerService.runJob(23L);

        assertEquals(CrawlerTaskStatus.FAILED, result.getStatus());
        assertEquals(2, job.getAntiBotBlocks()); // was 1, now 2
    }

    // ── checkThresholds — alert evaluation branches ───────────────────

    @Test
    void checkThresholds_allMetricsOk_noAlertSent() {
        when(crawlerProps.getSuccessRateThreshold()).thenReturn(95.0);
        when(crawlerProps.getLatencyP95ThresholdMs()).thenReturn(2000);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);

        when(crawlerTaskRepository.findSuccessRateByJobIdSince(eq(1L), any())).thenReturn(1.0);
        when(crawlerTaskRepository.findP95LatencyByJobIdSince(eq(1L), any())).thenReturn(500);

        CrawlerJobEntity job = new CrawlerJobEntity();
        job.setId(1L);
        job.setAntiBotBlocks(0);
        when(crawlerJobRepository.findById(1L)).thenReturn(Optional.of(job));

        crawlerService.checkThresholds(1L);

        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void checkThresholds_lowSuccessRate_sendsAlert() {
        when(crawlerProps.getSuccessRateThreshold()).thenReturn(95.0);
        when(crawlerProps.getLatencyP95ThresholdMs()).thenReturn(2000);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);
        when(crawlerProps.getSnapshotCap()).thenReturn(50);

        when(crawlerTaskRepository.findSuccessRateByJobIdSince(eq(2L), any())).thenReturn(0.5);
        when(crawlerTaskRepository.findP95LatencyByJobIdSince(eq(2L), any())).thenReturn(500);

        CrawlerJobEntity job = new CrawlerJobEntity();
        job.setId(2L);
        job.setAntiBotBlocks(0);
        when(crawlerJobRepository.findById(2L)).thenReturn(Optional.of(job));
        when(userRoleRepository.findByRole(any())).thenReturn(List.of());
        when(crawlerTaskRepository.findByJobIdAndStatus(eq(2L), eq(CrawlerTaskStatus.FAILED),
                any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        crawlerService.checkThresholds(2L);

        verify(auditService).log(isNull(), eq("CRAWLER_ALERT"), eq("CrawlerJob"), eq(2L), anyString());
    }

    @Test
    void checkThresholds_highLatency_sendsAlert() {
        when(crawlerProps.getSuccessRateThreshold()).thenReturn(95.0);
        when(crawlerProps.getLatencyP95ThresholdMs()).thenReturn(2000);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);
        when(crawlerProps.getSnapshotCap()).thenReturn(50);

        when(crawlerTaskRepository.findSuccessRateByJobIdSince(eq(3L), any())).thenReturn(1.0);
        when(crawlerTaskRepository.findP95LatencyByJobIdSince(eq(3L), any())).thenReturn(5000);

        CrawlerJobEntity job = new CrawlerJobEntity();
        job.setId(3L);
        job.setAntiBotBlocks(0);
        when(crawlerJobRepository.findById(3L)).thenReturn(Optional.of(job));
        when(userRoleRepository.findByRole(any())).thenReturn(List.of());
        when(crawlerTaskRepository.findByJobIdAndStatus(eq(3L), eq(CrawlerTaskStatus.FAILED),
                any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        crawlerService.checkThresholds(3L);

        verify(auditService).log(isNull(), eq("CRAWLER_ALERT"), eq("CrawlerJob"), eq(3L), anyString());
    }

    @Test
    void checkThresholds_antiBotBlocksPresent_sendsAlert() {
        when(crawlerProps.getSuccessRateThreshold()).thenReturn(95.0);
        when(crawlerProps.getLatencyP95ThresholdMs()).thenReturn(2000);
        when(appProperties.getCrawler()).thenReturn(crawlerProps);
        when(crawlerProps.getSnapshotCap()).thenReturn(50);

        when(crawlerTaskRepository.findSuccessRateByJobIdSince(eq(4L), any())).thenReturn(1.0);
        when(crawlerTaskRepository.findP95LatencyByJobIdSince(eq(4L), any())).thenReturn(100);

        CrawlerJobEntity job = new CrawlerJobEntity();
        job.setId(4L);
        job.setAntiBotBlocks(3);
        when(crawlerJobRepository.findById(4L)).thenReturn(Optional.of(job));
        when(userRoleRepository.findByRole(any())).thenReturn(List.of());
        when(crawlerTaskRepository.findByJobIdAndStatus(eq(4L), eq(CrawlerTaskStatus.FAILED),
                any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        crawlerService.checkThresholds(4L);

        verify(auditService).log(isNull(), eq("CRAWLER_ALERT"), eq("CrawlerJob"), eq(4L), anyString());
    }

    // ── helpers ───────────────────────────────────────────────────────

    private CrawlerJobEntity buildFileJob(Long id, String path) {
        CrawlerJobEntity job = new CrawlerJobEntity();
        job.setId(id);
        job.setSourceType(CrawlerSourceType.FILE);
        job.setSourcePath(path);
        job.setAntiBotBlocks(0);
        return job;
    }
}
