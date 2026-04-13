package com.campusstore.core.service;

import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.CrawlerTaskStatus;
import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.domain.model.Role;
import com.campusstore.infrastructure.persistence.entity.CrawlerJobEntity;
import com.campusstore.infrastructure.persistence.entity.CrawlerTaskEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.repository.CrawlerJobRepository;
import com.campusstore.infrastructure.persistence.repository.CrawlerTaskRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final CrawlerJobRepository crawlerJobRepository;
    private final CrawlerTaskRepository crawlerTaskRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final com.campusstore.infrastructure.config.AppProperties appProperties;

    public CrawlerService(CrawlerJobRepository crawlerJobRepository,
                          CrawlerTaskRepository crawlerTaskRepository,
                          UserRoleRepository userRoleRepository,
                          NotificationService notificationService,
                          AuditService auditService,
                          com.campusstore.infrastructure.config.AppProperties appProperties) {
        this.crawlerJobRepository = crawlerJobRepository;
        this.crawlerTaskRepository = crawlerTaskRepository;
        this.appProperties = appProperties;
        this.userRoleRepository = userRoleRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CrawlerJobEntity> listJobs() {
        return crawlerJobRepository.findAll();
    }

    public CrawlerJobEntity createJob(String name, String sourceType, String sourcePath,
                                       String cronExpression, Long createdByUserId) {
        Objects.requireNonNull(name, "Job name must not be null");
        Objects.requireNonNull(sourceType, "Source type must not be null");
        Objects.requireNonNull(sourcePath, "Source path must not be null");

        com.campusstore.core.domain.model.CrawlerSourceType parsedType;
        try {
            parsedType = com.campusstore.core.domain.model.CrawlerSourceType.valueOf(sourceType);
        } catch (IllegalArgumentException e) {
            throw new com.campusstore.core.domain.event.BusinessException(
                    "Invalid source type: " + sourceType + ". Must be FILE or INTRANET_PAGE");
        }

        // Offline/on-prem guarantee: reject sources that leave the local/intranet boundary.
        validateOfflineSource(parsedType, sourcePath);

        CrawlerJobEntity job = new CrawlerJobEntity();
        job.setName(name);
        job.setSourceType(parsedType);
        job.setSourcePath(sourcePath);
        job.setCronExpression(cronExpression);
        job.setIsActive(true);
        job.setSuccessRate(BigDecimal.ONE);
        job.setAvgLatencyMs(0);
        job.setParseHitRate(BigDecimal.ONE);
        job.setAntiBotBlocks(0);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());

        CrawlerJobEntity saved = crawlerJobRepository.save(job);

        auditService.log(createdByUserId, "CREATE_CRAWLER_JOB", "CrawlerJob", saved.getId(),
                "{\"name\":\"" + name + "\",\"sourceType\":\"" + sourceType + "\"}");
        log.info("Crawler job created id={}, name={}", saved.getId(), name);
        return saved;
    }

    public CrawlerJobEntity updateJob(Long jobId, String name, String sourceType, String sourcePath,
                                       String cronExpression, Boolean isActive, Long updatedByUserId) {
        Objects.requireNonNull(jobId, "Job ID must not be null");

        CrawlerJobEntity job = crawlerJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("CrawlerJob", jobId));

        if (name != null) {
            job.setName(name);
        }
        if (sourceType != null) {
            job.setSourceType(com.campusstore.core.domain.model.CrawlerSourceType.valueOf(sourceType));
        }
        if (sourcePath != null) {
            validateOfflineSource(job.getSourceType(), sourcePath);
            job.setSourcePath(sourcePath);
        }
        if (cronExpression != null) {
            job.setCronExpression(cronExpression);
        }
        if (isActive != null) {
            job.setIsActive(isActive);
        }
        job.setUpdatedAt(Instant.now());

        CrawlerJobEntity saved = crawlerJobRepository.save(job);

        auditService.log(updatedByUserId, "UPDATE_CRAWLER_JOB", "CrawlerJob", jobId, "{}");
        log.info("Crawler job updated id={}", jobId);
        return saved;
    }

    public CrawlerTaskEntity runJob(Long jobId) {
        Objects.requireNonNull(jobId, "Job ID must not be null");

        CrawlerJobEntity job = crawlerJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("CrawlerJob", jobId));

        // Mark job as running
        job.setLastRunAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        crawlerJobRepository.save(job);

        CrawlerTaskEntity task = new CrawlerTaskEntity();
        task.setJob(job);
        task.setStatus(CrawlerTaskStatus.RUNNING);
        task.setParserVersion("1.0.0");
        task.setStartedAt(Instant.now());
        task.setCreatedAt(Instant.now());

        Instant startTime = Instant.now();
        String rawContent = null;
        boolean parseHit = false;
        boolean antiBotBlocked = false;

        try {
            // Fetch content based on source type
            com.campusstore.core.domain.model.CrawlerSourceType sourceType = job.getSourceType();
            String sourcePath = job.getSourcePath();

            if (sourceType == com.campusstore.core.domain.model.CrawlerSourceType.FILE) {
                rawContent = readLocalFile(sourcePath);
            } else if (sourceType == com.campusstore.core.domain.model.CrawlerSourceType.INTRANET_PAGE) {
                rawContent = fetchUrl(sourcePath);
            } else {
                throw new RuntimeException("Unsupported source type: " + sourceType);
            }

            // Parse content (simple HTML/text extraction)
            if (rawContent != null && !rawContent.isBlank()) {
                // Anti-bot page may still be 200 OK — inspect body markers.
                if (isAntiBotContent(rawContent)) {
                    antiBotBlocked = true;
                    throw new RuntimeException("ANTI_BOT_BLOCK: challenge/denial page detected for " + sourcePath);
                }
                String parsed = extractTextContent(rawContent);
                parseHit = parsed != null && !parsed.isBlank();
            }

            Instant endTime = Instant.now();
            int latencyMs = (int) ChronoUnit.MILLIS.between(startTime, endTime);

            task.setStatus(CrawlerTaskStatus.SUCCESS);
            task.setCompletedAt(endTime);
            task.setLatencyMs(latencyMs);
            task.setParseHit(parseHit);

            log.info("Crawler job id={} completed successfully, latency={}ms, parseHit={}",
                    jobId, latencyMs, parseHit);

        } catch (Exception e) {
            Instant endTime = Instant.now();
            int latencyMs = (int) ChronoUnit.MILLIS.between(startTime, endTime);

            task.setStatus(CrawlerTaskStatus.FAILED);
            task.setCompletedAt(endTime);
            task.setLatencyMs(latencyMs);
            task.setParseHit(false);
            task.setErrorMessage(e.getMessage());

            // Flag anti-bot failures so job-level governance metrics can track them.
            if (!antiBotBlocked && e.getMessage() != null && e.getMessage().startsWith("ANTI_BOT_BLOCK")) {
                antiBotBlocked = true;
            }

            // Store raw content blob for failed tasks
            if (rawContent != null) {
                task.setRawContent(rawContent.getBytes(StandardCharsets.UTF_8));
            }

            log.error("Crawler job id={} failed: {}", jobId, e.getMessage());
        }

        // Persist anti-bot blocks on the job so the observability dashboard / alerting can use it.
        if (antiBotBlocked) {
            int prior = job.getAntiBotBlocks() != null ? job.getAntiBotBlocks() : 0;
            job.setAntiBotBlocks(prior + 1);
        }

        CrawlerTaskEntity savedTask = crawlerTaskRepository.save(task);

        // Enforce 50-sample cap for failed tasks (delete oldest if over)
        if (task.getStatus() == CrawlerTaskStatus.FAILED) {
            enforceFailureSampleCap(jobId);
        }

        // Update job metrics
        updateJobMetrics(job);

        return savedTask;
    }

    public void checkThresholds(Long jobId) {
        Objects.requireNonNull(jobId, "Job ID must not be null");

        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        // Calculate success rate over last 1 hour
        Double successRate = crawlerTaskRepository.findSuccessRateByJobIdSince(jobId, oneHourAgo);

        // Calculate p95 latency over last 1 hour
        Integer p95Latency = crawlerTaskRepository.findP95LatencyByJobIdSince(jobId, oneHourAgo);

        boolean alertNeeded = false;
        StringBuilder alertMessage = new StringBuilder();

        // Thresholds driven by AppProperties (campusstore.crawler.*) — success rate stored as
        // percentage (e.g. 95.0), successRate from repo is a ratio (0-1).
        double successRateThresholdRatio = appProperties.getCrawler().getSuccessRateThreshold() / 100.0;
        int latencyThresholdMs = appProperties.getCrawler().getLatencyP95ThresholdMs();

        if (successRate != null && successRate < successRateThresholdRatio) {
            alertNeeded = true;
            alertMessage.append("Crawler job ").append(jobId)
                    .append(" success rate below threshold: ")
                    .append(String.format("%.2f%%", successRate * 100));
        }

        if (p95Latency != null && p95Latency > latencyThresholdMs) {
            if (alertNeeded) alertMessage.append("; ");
            alertNeeded = true;
            alertMessage.append("Crawler job ").append(jobId)
                    .append(" p95 latency above threshold: ")
                    .append(p95Latency).append("ms");
        }

        // Anti-bot block counter is a first-class governance signal: any non-zero count
        // surfaces on the threshold alert so admins can react (rotate source, back off, etc.).
        CrawlerJobEntity jobForAntiBot = crawlerJobRepository.findById(jobId).orElse(null);
        if (jobForAntiBot != null && jobForAntiBot.getAntiBotBlocks() != null
                && jobForAntiBot.getAntiBotBlocks() > 0) {
            if (alertNeeded) alertMessage.append("; ");
            alertNeeded = true;
            alertMessage.append("Crawler job ").append(jobId)
                    .append(" anti-bot blocks observed: ")
                    .append(jobForAntiBot.getAntiBotBlocks());
        }

        if (alertNeeded) {
            sendAlertToAdmins(alertMessage.toString(), jobId);
            snapshotFailedSamples(jobId);
            log.warn("Crawler threshold alert: {}", alertMessage);
        } else {
            log.debug("Crawler job id={} thresholds OK (successRate={}, p95Latency={})",
                    jobId, successRate, p95Latency);
        }
    }

    @Transactional(readOnly = true)
    public Page<CrawlerTaskEntity> getFailures(Long jobId, Pageable pageable) {
        Objects.requireNonNull(jobId, "Job ID must not be null");
        return crawlerTaskRepository.findByJobIdAndStatus(jobId, CrawlerTaskStatus.FAILED, pageable);
    }

    /**
     * Enforce offline/on-prem operation by rejecting sources that reach outside the local
     * filesystem or intranet. For FILE jobs: must be a local path (no scheme or file://).
     * For INTRANET_PAGE jobs: host must resolve to a loopback/site-local/link-local address
     * and scheme must be http(s). Prevents accidental crawling of external production sites.
     */
    private void validateOfflineSource(com.campusstore.core.domain.model.CrawlerSourceType type, String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new com.campusstore.core.domain.event.BusinessException("Source path must not be blank");
        }
        String trimmed = sourcePath.trim();
        if (type == com.campusstore.core.domain.model.CrawlerSourceType.FILE) {
            // Accept bare paths or file:// URIs; reject any network scheme.
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("http://") || lower.startsWith("https://")
                    || lower.startsWith("ftp://") || lower.startsWith("ftps://")) {
                throw new com.campusstore.core.domain.event.BusinessException(
                        "FILE source must be a local filesystem path, not a network URL");
            }
            return;
        }
        if (type == com.campusstore.core.domain.model.CrawlerSourceType.INTRANET_PAGE) {
            URL url;
            try {
                url = new URL(trimmed);
            } catch (java.net.MalformedURLException e) {
                throw new com.campusstore.core.domain.event.BusinessException(
                        "INTRANET_PAGE source must be a valid URL: " + trimmed);
            }
            String scheme = url.getProtocol();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new com.campusstore.core.domain.event.BusinessException(
                        "INTRANET_PAGE source must use http or https scheme");
            }
            String host = url.getHost();
            if (host == null || host.isBlank()) {
                throw new com.campusstore.core.domain.event.BusinessException(
                        "INTRANET_PAGE source must specify a host");
            }
            try {
                java.net.InetAddress addr = java.net.InetAddress.getByName(host);
                boolean local = addr.isLoopbackAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isAnyLocalAddress()
                        || "localhost".equalsIgnoreCase(host);
                if (!local) {
                    throw new com.campusstore.core.domain.event.BusinessException(
                            "INTRANET_PAGE source must target a local/intranet host; public hosts are not permitted in offline mode");
                }
            } catch (java.net.UnknownHostException e) {
                // In offline mode we may not resolve names; allow well-known intranet-shaped
                // hostnames (e.g. ".local", ".intranet", ".internal", no-dot hostnames).
                String hostLower = host.toLowerCase();
                boolean looksIntranet = !hostLower.contains(".")
                        || hostLower.endsWith(".local")
                        || hostLower.endsWith(".intranet")
                        || hostLower.endsWith(".internal")
                        || hostLower.endsWith(".lan");
                if (!looksIntranet) {
                    throw new com.campusstore.core.domain.event.BusinessException(
                            "INTRANET_PAGE host '" + host + "' could not be verified as local/intranet");
                }
            }
        }
    }

    private String readLocalFile(String filePath) {
        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + filePath, e);
        }
    }

    private String fetchUrl(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "CampusStore-Crawler/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // 403 (Forbidden) and 429 (Too Many Requests) are classic anti-bot signals;
                // surface them with a distinguishable prefix so runJob can increment the
                // anti_bot_blocks metric on the job.
                if (responseCode == 403 || responseCode == 429) {
                    throw new RuntimeException("ANTI_BOT_BLOCK: HTTP " + responseCode + " from " + urlString);
                }
                throw new RuntimeException("HTTP request failed with status: " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch URL: " + urlString, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Detect anti-bot signals in the content body (e.g., challenge pages that return
     * HTTP 200 but carry clear bot-mitigation markers). Keep the detector conservative —
     * only match well-known strings so legitimate content is not mis-flagged.
     */
    private boolean isAntiBotContent(String rawContent) {
        if (rawContent == null) return false;
        String sample = rawContent.length() > 8192 ? rawContent.substring(0, 8192) : rawContent;
        String lower = sample.toLowerCase();
        return lower.contains("access denied")
                || lower.contains("cf-chl-bypass")
                || lower.contains("cloudflare") && lower.contains("challenge")
                || lower.contains("are you a robot")
                || lower.contains("captcha")
                || lower.contains("rate limited");
    }

    private String extractTextContent(String rawContent) {
        if (rawContent == null) return null;
        // Simple HTML tag removal and text extraction
        String text = rawContent.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        text = text.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");
        text = text.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private void enforceFailureSampleCap(Long jobId) {
        List<CrawlerTaskEntity> failedTasks =
                crawlerTaskRepository.findByJobIdAndStatus(jobId, CrawlerTaskStatus.FAILED);

        int cap = appProperties.getCrawler().getSnapshotCap();
        if (failedTasks.size() > cap) {
            // Sort by createdAt ascending to find oldest
            failedTasks.sort((a, b) -> {
                Instant aTime = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.MIN;
                Instant bTime = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.MIN;
                return aTime.compareTo(bTime);
            });

            int toDelete = failedTasks.size() - cap;
            List<CrawlerTaskEntity> oldest = failedTasks.subList(0, toDelete);
            crawlerTaskRepository.deleteAll(oldest);
            log.info("Deleted {} oldest failure samples for job id={}", toDelete, jobId);
        }
    }

    private void updateJobMetrics(CrawlerJobEntity job) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        Double successRate = crawlerTaskRepository.findSuccessRateByJobIdSince(job.getId(), oneHourAgo);
        if (successRate != null) {
            job.setSuccessRate(BigDecimal.valueOf(successRate).setScale(4, RoundingMode.HALF_UP));
        }

        Integer p95Latency = crawlerTaskRepository.findP95LatencyByJobIdSince(job.getId(), oneHourAgo);
        if (p95Latency != null) {
            job.setAvgLatencyMs(p95Latency);
        }

        // Parse hit rate from recent successful tasks
        List<CrawlerTaskEntity> successTasks =
                crawlerTaskRepository.findByJobIdAndStatus(job.getId(), CrawlerTaskStatus.SUCCESS);
        if (!successTasks.isEmpty()) {
            long parseHitCount = successTasks.stream()
                    .filter(t -> Boolean.TRUE.equals(t.getParseHit()))
                    .count();
            job.setParseHitRate(BigDecimal.valueOf((double) parseHitCount / successTasks.size())
                    .setScale(4, RoundingMode.HALF_UP));
        }

        job.setUpdatedAt(Instant.now());
        crawlerJobRepository.save(job);
    }

    private void snapshotFailedSamples(Long jobId) {
        // Create a dedicated threshold-breach snapshot task with raw content from the most recent failure
        List<CrawlerTaskEntity> recentFailures = crawlerTaskRepository.findByJobIdAndStatus(
                jobId, CrawlerTaskStatus.FAILED,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        if (!recentFailures.isEmpty()) {
            CrawlerTaskEntity latestFailure = recentFailures.get(0);
            CrawlerJobEntity job = crawlerJobRepository.findById(jobId).orElse(null);
            if (job != null) {
                // Create a snapshot record capturing the state at threshold breach time
                CrawlerTaskEntity snapshot = new CrawlerTaskEntity();
                snapshot.setJob(job);
                snapshot.setStatus(CrawlerTaskStatus.FAILED);
                snapshot.setParserVersion(latestFailure.getParserVersion());
                snapshot.setRawContent(latestFailure.getRawContent());
                snapshot.setErrorMessage("THRESHOLD_BREACH_SNAPSHOT: " +
                        (latestFailure.getErrorMessage() != null ? latestFailure.getErrorMessage() : "threshold breach"));
                snapshot.setStartedAt(Instant.now());
                snapshot.setCompletedAt(Instant.now());
                snapshot.setCreatedAt(Instant.now());
                crawlerTaskRepository.save(snapshot);

                // Enforce the 50-sample cap per job
                enforceSnapshotCap(jobId);
                log.info("Created threshold-breach snapshot for crawler job id={}", jobId);
            }
        } else {
            log.info("No failed samples available for snapshot on crawler job id={}", jobId);
        }
    }

    private void enforceSnapshotCap(Long jobId) {
        long failedCount = crawlerTaskRepository.findByJobIdAndStatus(
                jobId, CrawlerTaskStatus.FAILED,
                PageRequest.of(0, Integer.MAX_VALUE)
        ).getTotalElements();

        int cap = appProperties.getCrawler().getSnapshotCap();
        if (failedCount > cap) {
            // Delete oldest failures beyond the cap
            List<CrawlerTaskEntity> oldest = crawlerTaskRepository.findByJobIdAndStatus(
                    jobId, CrawlerTaskStatus.FAILED,
                    PageRequest.of(0, (int) (failedCount - cap),
                            Sort.by(Sort.Direction.ASC, "createdAt"))
            ).getContent();
            crawlerTaskRepository.deleteAll(oldest);
            log.info("Enforced {}-sample cap: deleted {} oldest snapshots for job {}",
                    cap, oldest.size(), jobId);
        }
    }

    private void sendAlertToAdmins(String message, Long jobId) {
        List<UserRoleEntity> adminRoles = userRoleRepository.findByRole(Role.ADMIN);
        String title = "Crawler Alert: Job " + jobId;

        for (UserRoleEntity adminRole : adminRoles) {
            Long adminUserId = adminRole.getUserId();
            if (adminUserId != null) {
                try {
                    notificationService.sendNotification(
                            adminUserId,
                            NotificationType.CRAWLER_ALERT,
                            title,
                            message,
                            true,
                            "CRAWLER_JOB",
                            jobId
                    );
                } catch (Exception e) {
                    log.warn("Failed to send crawler alert to admin user {}: {}", adminUserId, e.getMessage());
                }
            }
        }

        // Log the alert in audit
        auditService.log(null, "CRAWLER_ALERT", "CrawlerJob", jobId,
                "{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
