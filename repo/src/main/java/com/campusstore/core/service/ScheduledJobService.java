package com.campusstore.core.service;

import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.infrastructure.persistence.entity.CrawlerJobEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.persistence.repository.CrawlerJobRepository;
import com.campusstore.infrastructure.persistence.repository.ItemRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ScheduledJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobService.class);

    private final AuditService auditService;
    private final InventoryService inventoryService;
    private final CrawlerService crawlerService;
    private final NotificationService notificationService;
    private final ItemRequestRepository itemRequestRepository;
    private final CrawlerJobRepository crawlerJobRepository;

    public ScheduledJobService(AuditService auditService, InventoryService inventoryService,
                                CrawlerService crawlerService, NotificationService notificationService,
                                ItemRequestRepository itemRequestRepository,
                                CrawlerJobRepository crawlerJobRepository) {
        this.auditService = auditService;
        this.inventoryService = inventoryService;
        this.crawlerService = crawlerService;
        this.notificationService = notificationService;
        this.itemRequestRepository = itemRequestRepository;
        this.crawlerJobRepository = crawlerJobRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runDailyRetentionCleanup() {
        log.info("Starting daily retention cleanup");
        try {
            auditService.runRetentionCleanup();
            log.info("Daily retention cleanup completed");
        } catch (Exception e) {
            log.error("Daily retention cleanup failed", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void runDailyUserDeletion() {
        log.info("Starting daily user deletion (cryptographic erasure)");
        try {
            auditService.runUserDeletion();
            log.info("Daily user deletion completed");
        } catch (Exception e) {
            log.error("Daily user deletion failed", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void runDailyAbcRecalculation() {
        log.info("Starting daily ABC recalculation");
        try {
            inventoryService.recalculateAbc();
            log.info("Daily ABC recalculation completed");
        } catch (Exception e) {
            log.error("Daily ABC recalculation failed", e);
        }
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void runDailyHeatScoreRecalculation() {
        log.info("Starting daily heat score recalculation");
        try {
            inventoryService.recalculateHeatScores();
            log.info("Daily heat score recalculation completed");
        } catch (Exception e) {
            log.error("Daily heat score recalculation failed", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void runHourlyOverdueCheck() {
        log.info("Starting hourly overdue check");
        try {
            List<ItemRequestEntity> overdueRequests = itemRequestRepository
                    .findByStatusAndPickupDeadlineBefore(RequestStatus.READY_FOR_PICKUP, Instant.now());

            for (ItemRequestEntity request : overdueRequests) {
                request.setStatus(RequestStatus.OVERDUE);
                request.setUpdatedAt(Instant.now());
                itemRequestRepository.save(request);

                notificationService.sendNotification(
                        request.getRequesterId(),
                        NotificationType.PICKUP_OVERDUE,
                        "Pickup Overdue",
                        "Your request #" + request.getId() + " is overdue for pickup. Please collect your item.",
                        false, "ITEM_REQUEST", request.getId()
                );
            }

            if (!overdueRequests.isEmpty()) {
                log.info("Marked {} requests as overdue", overdueRequests.size());
            }

            // Additionally, send escalation for requests overdue by 24+ hours
            Instant overdue24hCutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            List<ItemRequestEntity> longOverdue = itemRequestRepository
                    .findByStatusAndPickupDeadlineBefore(RequestStatus.OVERDUE, overdue24hCutoff);
            for (ItemRequestEntity request : longOverdue) {
                // Only send if pickup deadline + 24h has passed (i.e., overdue for 24h)
                if (request.getPickupDeadline() != null
                        && request.getPickupDeadline().plus(24, ChronoUnit.HOURS).isBefore(Instant.now())) {
                    notificationService.sendNotification(
                            request.getRequesterId(),
                            NotificationType.PICKUP_OVERDUE,
                            "Pickup Overdue - 24 Hour Escalation",
                            "Your request #" + request.getId() + " has been overdue for more than 24 hours. Immediate pickup required.",
                            true, "ITEM_REQUEST", request.getId()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Hourly overdue check failed", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void runHourlyMissedCheckinCheck() {
        log.info("Starting hourly missed check-in check");
        try {
            Instant cutoff = Instant.now().minus(48, ChronoUnit.HOURS);
            List<ItemRequestEntity> stalePickingRequests = itemRequestRepository
                    .findByStatusAndUpdatedAtBefore(RequestStatus.PICKING, cutoff);

            for (ItemRequestEntity request : stalePickingRequests) {
                notificationService.sendNotification(
                        request.getRequesterId(),
                        NotificationType.MISSED_CHECKIN,
                        "Missed Check-In",
                        "Your request #" + request.getId() + " has not been checked in for over 48 hours. Please follow up.",
                        false, "ITEM_REQUEST", request.getId()
                );
            }

            if (!stalePickingRequests.isEmpty()) {
                log.info("Sent {} missed check-in notifications", stalePickingRequests.size());
            }
        } catch (Exception e) {
            log.error("Hourly missed check-in check failed", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void runHourlyCrawlerThresholdCheck() {
        log.info("Starting hourly crawler threshold check");
        try {
            List<CrawlerJobEntity> activeJobs = crawlerJobRepository.findByIsActiveTrue();
            for (CrawlerJobEntity job : activeJobs) {
                try {
                    crawlerService.checkThresholds(job.getId());
                } catch (Exception e) {
                    log.error("Threshold check failed for crawler job {}", job.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Hourly crawler threshold check failed", e);
        }
    }
}
