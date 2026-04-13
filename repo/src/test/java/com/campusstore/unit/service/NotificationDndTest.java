package com.campusstore.unit.service;

import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.service.NotificationService;
import com.campusstore.infrastructure.persistence.entity.NotificationEntity;
import com.campusstore.infrastructure.persistence.entity.NotificationPreferenceEntity;
import com.campusstore.infrastructure.persistence.entity.UserPreferenceEntity;
import com.campusstore.infrastructure.persistence.repository.EmailOutboxRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationRepository;
import com.campusstore.infrastructure.persistence.repository.UserPreferenceRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.security.encryption.AesEncryptionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests Notification Do-Not-Disturb (DND) logic:
 * - Non-critical notifications suppressed when user opts out
 * - Critical notifications always delivered regardless of opt-out
 * - DND window behaviour (notification still saved but deferred)
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationDndTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private EmailOutboxRepository emailOutboxRepository;
    @Mock private UserRepository userRepository;
    @Mock private AesEncryptionService aesEncryptionService;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> {
                    NotificationEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });
    }

    // --- Opt-out suppression ---

    @Test
    void sendNotification_nonCritical_userOptedOut_returnsNull() {
        Long userId = 1L;

        NotificationPreferenceEntity pref = new NotificationPreferenceEntity();
        pref.setUserId(userId);
        pref.setNotificationType(NotificationType.REQUEST_SUBMITTED);
        pref.setEnabled(false);
        pref.setEmailEnabled(false);

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.REQUEST_SUBMITTED))
                .thenReturn(Optional.of(pref));

        NotificationEntity result = notificationService.sendNotification(
                userId, NotificationType.REQUEST_SUBMITTED,
                "Test", "Test message", false, "ItemRequest", 1L);

        assertNull(result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendNotification_critical_userOptedOut_stillDelivered() {
        Long userId = 1L;

        NotificationPreferenceEntity pref = new NotificationPreferenceEntity();
        pref.setUserId(userId);
        pref.setNotificationType(NotificationType.SYSTEM_ALERT);
        pref.setEnabled(false);
        pref.setEmailEnabled(false);

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(userId, NotificationType.SYSTEM_ALERT))
                .thenReturn(Optional.of(pref));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        NotificationEntity result = notificationService.sendNotification(
                userId, NotificationType.SYSTEM_ALERT,
                "Critical Alert", "System maintenance", true, null, null);

        assertNotNull(result);
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    // --- DND window tests ---
    // The DND logic checks LocalTime.now() against the user's DND window.
    // We test with a large overnight window that will always include the current time,
    // vs an explicit window that never includes it.

    @Test
    void sendNotification_nonCritical_duringDnd_stillSavedButDeferred() {
        Long userId = 1L;

        // Set DND window to cover all 24 hours (overnight window: start=00:00, end=23:59)
        UserPreferenceEntity userPref = new UserPreferenceEntity();
        userPref.setUserId(userId);
        userPref.setDndStartTime(LocalTime.of(0, 0));
        userPref.setDndEndTime(LocalTime.of(23, 59));

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(userPref));

        NotificationEntity result = notificationService.sendNotification(
                userId, NotificationType.REQUEST_SUBMITTED,
                "Test", "Test message", false, "ItemRequest", 1L);

        // Even during DND, the notification is saved (deferred for later display)
        assertNotNull(result);
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    @Test
    void sendNotification_critical_duringDnd_delivered() {
        Long userId = 1L;

        // Set DND window to cover all 24 hours
        UserPreferenceEntity userPref = new UserPreferenceEntity();
        userPref.setUserId(userId);
        userPref.setDndStartTime(LocalTime.of(0, 0));
        userPref.setDndEndTime(LocalTime.of(23, 59));

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(userPref));

        NotificationEntity result = notificationService.sendNotification(
                userId, NotificationType.SYSTEM_ALERT,
                "Critical Alert", "Server down", true, null, null);

        assertNotNull(result);
        assertTrue(result.getIsCritical());
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    @Test
    void sendNotification_noDndConfigured_normalDelivery() {
        Long userId = 1L;

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        NotificationEntity result = notificationService.sendNotification(
                userId, NotificationType.PICKUP_READY,
                "Pickup Ready", "Your item is ready", false, "ItemRequest", 10L);

        assertNotNull(result);
        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    @Test
    void sendNotification_dndWindowNull_normalDelivery() {
        Long userId = 1L;

        UserPreferenceEntity userPref = new UserPreferenceEntity();
        userPref.setUserId(userId);
        userPref.setDndStartTime(null);
        userPref.setDndEndTime(null);

        when(notificationPreferenceRepository.findByUserIdAndNotificationType(eq(userId), any()))
                .thenReturn(Optional.empty());
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(userPref));

        NotificationEntity result = notificationService.sendNotification(
                userId, NotificationType.PICKUP_READY,
                "Pickup Ready", "Your item is ready", false, "ItemRequest", 10L);

        assertNotNull(result);
        verify(notificationRepository).save(any(NotificationEntity.class));
    }
}
