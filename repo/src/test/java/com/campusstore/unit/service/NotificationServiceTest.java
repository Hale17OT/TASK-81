package com.campusstore.unit.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.service.NotificationService;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.persistence.entity.NotificationPreferenceEntity;
import com.campusstore.infrastructure.persistence.repository.EmailOutboxRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService} covering ownership enforcement
 * on markRead, bulk markAllRead, preference upsert, and unread count delegation.
 * DND and opt-out suppression are covered by {@link NotificationDndTest}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private EmailOutboxRepository emailOutboxRepository;
    @Mock private UserRepository userRepository;
    @Mock private AesEncryptionService aesEncryptionService;

    @InjectMocks
    private NotificationService notificationService;

    // ── markRead ──────────────────────────────────────────────────────

    @Test
    void markRead_notFound_throwsResourceNotFoundException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.markRead(99L, 1L));
    }

    @Test
    void markRead_otherUsersNotification_throwsAccessDeniedException() {
        NotificationEntity n = new NotificationEntity();
        n.setId(1L);
        n.setUserId(2L); // belongs to user 2
        n.setIsRead(false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        assertThrows(AccessDeniedException.class,
                () -> notificationService.markRead(1L, 1L)); // user 1 trying to mark user 2's notification
    }

    @Test
    void markRead_ownNotification_setsReadFlag() {
        NotificationEntity n = new NotificationEntity();
        n.setId(1L);
        n.setUserId(1L);
        n.setIsRead(false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markRead(1L, 1L);

        assertTrue(n.getIsRead());
        assertNotNull(n.getReadAt());
        verify(notificationRepository).save(n);
    }

    // ── markAllRead ───────────────────────────────────────────────────

    @Test
    void markAllRead_marksAllUnreadNotifications() {
        NotificationEntity n1 = new NotificationEntity();
        n1.setIsRead(false);
        NotificationEntity n2 = new NotificationEntity();
        n2.setIsRead(false);
        when(notificationRepository.findByUserIdAndIsReadFalse(1L)).thenReturn(List.of(n1, n2));
        when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markAllRead(1L);

        assertTrue(n1.getIsRead());
        assertTrue(n2.getIsRead());
        assertNotNull(n1.getReadAt());
        assertNotNull(n2.getReadAt());
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }

    @Test
    void markAllRead_noUnread_savesEmptyList() {
        when(notificationRepository.findByUserIdAndIsReadFalse(1L)).thenReturn(List.of());

        notificationService.markAllRead(1L);

        verify(notificationRepository).saveAll(List.of());
    }

    @Test
    void markAllRead_nullUserId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> notificationService.markAllRead(null));
    }

    // ── getUnreadCount ────────────────────────────────────────────────

    @Test
    void getUnreadCount_delegatesToRepository() {
        when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(7L);

        long count = notificationService.getUnreadCount(1L);

        assertEquals(7L, count);
    }

    @Test
    void getUnreadCount_nullUserId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> notificationService.getUnreadCount(null));
    }

    // ── updatePreferences ─────────────────────────────────────────────

    @Test
    void updatePreferences_newType_createsEntry() {
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(1L, NotificationType.REQUEST_SUBMITTED))
                .thenReturn(Optional.empty());
        when(notificationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceEntity result = notificationService.updatePreferences(
                1L, NotificationType.REQUEST_SUBMITTED, true, false);

        assertEquals(1L, result.getUserId());
        assertEquals(NotificationType.REQUEST_SUBMITTED, result.getNotificationType());
        assertTrue(result.getEnabled());
        assertFalse(result.getEmailEnabled());
    }

    @Test
    void updatePreferences_existingType_updatesInPlace() {
        NotificationPreferenceEntity existing = new NotificationPreferenceEntity();
        existing.setUserId(1L);
        existing.setNotificationType(NotificationType.PICKUP_READY);
        existing.setEnabled(false);
        existing.setEmailEnabled(false);
        when(notificationPreferenceRepository.findByUserIdAndNotificationType(1L, NotificationType.PICKUP_READY))
                .thenReturn(Optional.of(existing));
        when(notificationPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceEntity result = notificationService.updatePreferences(
                1L, NotificationType.PICKUP_READY, true, true);

        assertTrue(result.getEnabled());
        assertTrue(result.getEmailEnabled());
        verify(notificationPreferenceRepository).save(existing);
    }

    @Test
    void updatePreferences_nullUserId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> notificationService.updatePreferences(null, NotificationType.PICKUP_READY, true, false));
    }
}
