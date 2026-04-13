package com.campusstore.core.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.EmailOutboxStatus;
import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.infrastructure.persistence.entity.EmailOutboxEntity;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.persistence.entity.NotificationPreferenceEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import com.campusstore.infrastructure.persistence.repository.EmailOutboxRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final UserRepository userRepository;
    private final AesEncryptionService aesEncryptionService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository notificationPreferenceRepository,
                               UserPreferenceRepository userPreferenceRepository,
                               EmailOutboxRepository emailOutboxRepository,
                               UserRepository userRepository,
                               AesEncryptionService aesEncryptionService) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.userRepository = userRepository;
        this.aesEncryptionService = aesEncryptionService;
    }

    public NotificationEntity sendNotification(Long userId, NotificationType type, String title,
                                                String message, boolean isCritical,
                                                String referenceType, Long referenceId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(type, "Notification type must not be null");

        // Check if user has opted out of this notification type
        Optional<NotificationPreferenceEntity> prefOpt =
                notificationPreferenceRepository.findByUserIdAndNotificationType(userId, type);
        if (prefOpt.isPresent() && Boolean.FALSE.equals(prefOpt.get().getEnabled())) {
            // User opted out, but critical admin notices always delivered
            if (!isCritical) {
                log.debug("Notification suppressed for user={} type={}: user opted out", userId, type);
                return null;
            }
        }

        // Check DND window
        boolean isDndActive = isInDndWindow(userId);
        boolean dndSuppressed = isDndActive && !isCritical;
        if (dndSuppressed) {
            // Still save but mark for later display
            log.debug("Notification deferred for user={}: DND active", userId);
        }

        // Save notification
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setIsCritical(isCritical);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setDndSuppressed(dndSuppressed);
        notification.setCreatedAt(Instant.now());

        NotificationEntity saved = notificationRepository.save(notification);

        // If user has email enabled for this type, create email outbox entry
        if (prefOpt.isPresent() && Boolean.TRUE.equals(prefOpt.get().getEmailEnabled())) {
            createEmailOutbox(userId, title, message);
        }

        log.info("Notification sent to user={}, type={}, critical={}", userId, type, isCritical);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> listNotifications(Long userId, Pageable pageable) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public void markRead(Long notificationId, Long userId) {
        Objects.requireNonNull(notificationId, "Notification ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (!userId.equals(notification.getUserId())) {
            throw new AccessDeniedException("Cannot mark another user's notification as read");
        }

        notification.setIsRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);

        log.debug("Notification id={} marked as read by user={}", notificationId, userId);
    }

    public void markAllRead(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");

        List<NotificationEntity> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        Instant now = Instant.now();
        for (NotificationEntity notification : unread) {
            notification.setIsRead(true);
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unread);

        log.info("All notifications marked as read for user={}, count={}", userId, unread.size());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceEntity> getPreferences(Long userId) {
        Objects.requireNonNull(userId, "User ID must not be null");
        return notificationPreferenceRepository.findByUserId(userId);
    }

    public NotificationPreferenceEntity updatePreferences(Long userId, NotificationType type,
                                                           Boolean enabled, Boolean emailEnabled) {
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(type, "Notification type must not be null");

        Optional<NotificationPreferenceEntity> existing =
                notificationPreferenceRepository.findByUserIdAndNotificationType(userId, type);

        NotificationPreferenceEntity pref;
        if (existing.isPresent()) {
            pref = existing.get();
        } else {
            pref = new NotificationPreferenceEntity();
            pref.setUserId(userId);
            pref.setNotificationType(type);
        }

        if (enabled != null) {
            pref.setEnabled(enabled);
        }
        if (emailEnabled != null) {
            pref.setEmailEnabled(emailEnabled);
        }

        NotificationPreferenceEntity saved = notificationPreferenceRepository.save(pref);
        log.info("Notification preference updated for user={}, type={}", userId, type);
        return saved;
    }

    public byte[] exportOutbox() {
        List<EmailOutboxEntity> queued = emailOutboxRepository.findByStatusOrderByCreatedAtAsc(EmailOutboxStatus.QUEUED);

        if (queued.isEmpty()) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            int index = 0;
            for (EmailOutboxEntity email : queued) {
                String emlContent = email.getEmlData();
                if (emlContent == null) {
                    emlContent = generateEml("noreply@campusstore.edu",
                            "user-" + email.getRecipientUserId() + "@campusstore.edu",
                            email.getSubject(),
                            email.getBodyText());
                }

                ZipEntry entry = new ZipEntry("email_" + (++index) + ".eml");
                zos.putNextEntry(entry);
                zos.write(emlContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                // Mark as exported
                email.setStatus(EmailOutboxStatus.EXPORTED);
                email.setExportedAt(Instant.now());
            }

            emailOutboxRepository.saveAll(queued);
            zos.finish();

            log.info("Exported {} emails from outbox", queued.size());
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to export email outbox", e);
            throw new RuntimeException("Failed to export email outbox", e);
        }
    }

    public String generateEml(String from, String to, String subject, String body) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        String dateHeader = DateTimeFormatter.RFC_1123_DATE_TIME.format(now);

        StringBuilder eml = new StringBuilder();
        eml.append("From: ").append(from != null ? from : "noreply@campusstore.edu").append("\r\n");
        eml.append("To: ").append(to != null ? to : "unknown@campusstore.edu").append("\r\n");
        eml.append("Subject: ").append(subject != null ? subject : "(no subject)").append("\r\n");
        eml.append("Date: ").append(dateHeader).append("\r\n");
        eml.append("MIME-Version: 1.0").append("\r\n");
        eml.append("Content-Type: text/plain; charset=UTF-8").append("\r\n");
        eml.append("\r\n");
        eml.append(body != null ? body : "");
        return eml.toString();
    }

    private boolean isInDndWindow(Long userId) {
        Optional<UserPreferenceEntity> prefOpt = userPreferenceRepository.findByUserId(userId);
        if (prefOpt.isEmpty()) {
            return false;
        }

        UserPreferenceEntity pref = prefOpt.get();
        LocalTime dndStart = pref.getDndStartTime();
        LocalTime dndEnd = pref.getDndEndTime();

        if (dndStart == null || dndEnd == null) {
            return false;
        }

        LocalTime now = LocalTime.now();
        if (dndStart.isBefore(dndEnd)) {
            // Same day window (e.g., 22:00 - 23:00)
            return !now.isBefore(dndStart) && now.isBefore(dndEnd);
        } else {
            // Overnight window (e.g., 22:00 - 07:00)
            return !now.isBefore(dndStart) || now.isBefore(dndEnd);
        }
    }

    private void createEmailOutbox(Long userId, String subject, String bodyText) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        String recipientEmail = null;
        if (user.getEmailEncrypted() != null) {
            recipientEmail = aesEncryptionService.decrypt(user.getEmailEncrypted());
        }

        String emlData = generateEml("noreply@campusstore.edu",
                recipientEmail != null ? recipientEmail : "user-" + userId + "@campusstore.edu",
                subject, bodyText);

        EmailOutboxEntity email = new EmailOutboxEntity();
        email.setRecipientUserId(userId);
        if (recipientEmail != null) {
            email.setRecipientAddressEncrypted(aesEncryptionService.encrypt(recipientEmail));
        }
        email.setSubject(subject);
        email.setBodyText(bodyText);
        email.setEmlData(emlData);
        email.setStatus(EmailOutboxStatus.QUEUED);
        email.setCreatedAt(Instant.now());
        emailOutboxRepository.save(email);

        log.debug("Email outbox entry created for user={}", userId);
    }
}
