package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.NotificationType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port for notification operations.
 */
public interface NotificationUseCase {

    /**
     * Send a notification to a user.
     *
     * @param userId   the recipient user id
     * @param type     the notification type
     * @param title    the notification title
     * @param message  the notification message body
     * @param critical whether this is a critical notification
     * @param refType  optional reference entity type (e.g. "REQUEST", "ITEM")
     * @param refId    optional reference entity id
     * @return the id of the created notification
     */
    Long sendNotification(Long userId, NotificationType type, String title, String message,
                          boolean critical, String refType, Long refId);

    /**
     * List notifications for a user.
     *
     * @param userId   the user id
     * @param pageable pagination information
     * @return a page of notification views
     */
    Page<NotificationView> listNotifications(Long userId, Pageable pageable);

    /**
     * Mark a single notification as read.
     *
     * @param notificationId the notification id
     * @param userId         the user id (for ownership validation)
     */
    void markRead(Long notificationId, Long userId);

    /**
     * Mark all notifications as read for a user.
     *
     * @param userId the user id
     */
    void markAllRead(Long userId);

    /**
     * Get the count of unread notifications for a user.
     *
     * @param userId the user id
     * @return the unread count
     */
    long getUnreadCount(Long userId);

    /**
     * Get a user's notification preferences.
     *
     * @param userId the user id
     * @return list of notification preference views
     */
    List<NotificationPrefView> getPreferences(Long userId);

    /**
     * Update a user's notification preferences.
     *
     * @param userId      the user id
     * @param preferences the preference updates
     */
    void updatePreferences(Long userId, List<NotificationPrefCommand> preferences);

    /**
     * Export the notification outbox (for external delivery systems).
     *
     * @return list of outbox entries ready for dispatch
     */
    List<OutboxEntry> exportOutbox();

    // ── Command types ──────────────────────────────────────────────────

    record NotificationPrefCommand(
            NotificationType type,
            boolean enabled,
            boolean emailEnabled
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record NotificationView(
            Long id,
            NotificationType type,
            String title,
            String message,
            boolean read,
            boolean critical,
            String referenceType,
            Long referenceId,
            Instant createdAt,
            Instant readAt
    ) {
    }

    record NotificationPrefView(
            NotificationType type,
            boolean enabled,
            boolean emailEnabled
    ) {
    }

    record OutboxEntry(
            Long notificationId,
            Long userId,
            NotificationType type,
            String title,
            String message,
            boolean critical,
            String referenceType,
            Long referenceId,
            Instant createdAt
    ) {
    }
}
