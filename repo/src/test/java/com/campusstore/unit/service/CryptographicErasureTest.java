package com.campusstore.unit.service;

import com.campusstore.core.domain.model.AccountStatus;
import com.campusstore.core.service.AuditService;
import com.campusstore.infrastructure.persistence.entity.AuditLogEntity;
import com.campusstore.infrastructure.persistence.entity.DataRetentionPolicyEntity;
import com.campusstore.infrastructure.persistence.entity.DeletedUserPlaceholderEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.AuditLogRepository;
import com.campusstore.infrastructure.persistence.repository.BrowsingHistoryRepository;
import com.campusstore.infrastructure.persistence.repository.DataRetentionPolicyRepository;
import com.campusstore.infrastructure.persistence.repository.DeletedUserPlaceholderRepository;
import com.campusstore.infrastructure.persistence.repository.DisputeRepository;
import com.campusstore.infrastructure.persistence.repository.EmailOutboxRepository;
import com.campusstore.infrastructure.persistence.repository.NotificationRepository;
import com.campusstore.infrastructure.persistence.repository.SearchLogRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests cryptographic erasure (user deletion):
 * - Placeholder created with hashed identity
 * - Audit logs updated with placeholder reference
 * - Open dispute blocks deletion
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CryptographicErasureTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private DataRetentionPolicyRepository dataRetentionPolicyRepository;
    @Mock private DeletedUserPlaceholderRepository deletedUserPlaceholderRepository;
    @Mock private UserRepository userRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private SearchLogRepository searchLogRepository;
    @Mock private BrowsingHistoryRepository browsingHistoryRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailOutboxRepository emailOutboxRepository;

    @InjectMocks
    private AuditService auditService;

    private UserEntity disabledUser;

    @BeforeEach
    void setUp() {
        disabledUser = new UserEntity();
        disabledUser.setId(42L);
        disabledUser.setUsername("oldstudent");
        disabledUser.setAccountStatus(AccountStatus.DISABLED);
        disabledUser.setDisabledAt(Instant.now().minus(60, ChronoUnit.DAYS));
    }

    @Test
    void runUserDeletion_createsPlaceholderWithHashedIdentity() {
        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(List.of(disabledUser));
        when(disputeRepository.existsByUserIdAndStatus(42L, com.campusstore.core.domain.model.DisputeStatus.OPEN)).thenReturn(false);

        // Return empty page of audit logs (no audit logs to update)
        Page<AuditLogEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findByActorUserId(eq(42L), any(Pageable.class))).thenReturn(emptyPage);

        when(deletedUserPlaceholderRepository.save(any(DeletedUserPlaceholderEntity.class)))
                .thenAnswer(inv -> {
                    DeletedUserPlaceholderEntity p = inv.getArgument(0);
                    p.setId(1L);
                    return p;
                });
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.runUserDeletion();

        // Verify placeholder created
        ArgumentCaptor<DeletedUserPlaceholderEntity> captor =
                ArgumentCaptor.forClass(DeletedUserPlaceholderEntity.class);
        verify(deletedUserPlaceholderRepository).save(captor.capture());

        DeletedUserPlaceholderEntity placeholder = captor.getValue();
        assertEquals(42L, placeholder.getOriginalUserId());
        assertNotNull(placeholder.getHashedIdentity());
        // SHA-256 hex string is 64 chars
        assertEquals(64, placeholder.getHashedIdentity().length());
        assertNotNull(placeholder.getDeletedAt());
    }

    @Test
    void runUserDeletion_appendsErasureMappingEventWithoutMutatingPriorLogs() {
        // Governance requirement: audit log is immutable. Erasure must NOT modify existing
        // audit rows; instead it must append a linked ERASURE_MAPPING event that records
        // actor_user_id -> placeholder_id so prior entries can still be resolved forensically.
        AuditLogEntity existingLog = new AuditLogEntity();
        existingLog.setId(100L);
        existingLog.setActorUserId(42L);
        existingLog.setAction("CREATE_REQUEST");

        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(List.of(disabledUser));
        when(disputeRepository.existsByUserIdAndStatus(42L, com.campusstore.core.domain.model.DisputeStatus.OPEN)).thenReturn(false);

        Page<AuditLogEntity> logsPage = new PageImpl<>(List.of(existingLog));
        when(auditLogRepository.findByActorUserId(eq(42L), any(Pageable.class)))
                .thenReturn(logsPage);

        when(deletedUserPlaceholderRepository.save(any(DeletedUserPlaceholderEntity.class)))
                .thenAnswer(inv -> {
                    DeletedUserPlaceholderEntity p = inv.getArgument(0);
                    p.setId(1L);
                    return p;
                });
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.runUserDeletion();

        // Prior audit row is untouched (immutability).
        assertEquals(42L, existingLog.getActorUserId());
        assertNull(existingLog.getActorPlaceholderId());
        assertNull(existingLog.getActorUsernameHash());

        // A linked ERASURE_MAPPING event must be appended.
        ArgumentCaptor<AuditLogEntity> savedLogs = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, atLeastOnce()).save(savedLogs.capture());

        boolean hasMappingEvent = savedLogs.getAllValues().stream()
                .anyMatch(e -> "ERASURE_MAPPING".equals(e.getAction())
                        && e.getActorPlaceholderId() != null
                        && e.getActorUsernameHash() != null
                        && e.getActorUsernameHash().length() == 64);
        assertTrue(hasMappingEvent, "Expected a linked ERASURE_MAPPING audit event to be appended");

        // The bulk-mutation path must NOT be used — audit rows are immutable.
        verify(auditLogRepository, never()).saveAll(anyList());
    }

    @Test
    void runUserDeletion_deletesUserAfterPlaceholderCreation() {
        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(List.of(disabledUser));
        when(disputeRepository.existsByUserIdAndStatus(42L, com.campusstore.core.domain.model.DisputeStatus.OPEN)).thenReturn(false);

        Page<AuditLogEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findByActorUserId(eq(42L), any(Pageable.class))).thenReturn(emptyPage);

        when(deletedUserPlaceholderRepository.save(any(DeletedUserPlaceholderEntity.class)))
                .thenAnswer(inv -> {
                    DeletedUserPlaceholderEntity p = inv.getArgument(0);
                    p.setId(1L);
                    return p;
                });
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.runUserDeletion();

        // Verify user hard-deleted
        verify(userRepository).delete(disabledUser);
    }

    @Test
    void runUserDeletion_logsErasureAuditEntry() {
        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(List.of(disabledUser));
        when(disputeRepository.existsByUserIdAndStatus(42L, com.campusstore.core.domain.model.DisputeStatus.OPEN)).thenReturn(false);

        Page<AuditLogEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findByActorUserId(eq(42L), any(Pageable.class))).thenReturn(emptyPage);

        when(deletedUserPlaceholderRepository.save(any(DeletedUserPlaceholderEntity.class)))
                .thenAnswer(inv -> {
                    DeletedUserPlaceholderEntity p = inv.getArgument(0);
                    p.setId(1L);
                    return p;
                });
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.runUserDeletion();

        // The service appends two audit events: ERASURE_MAPPING (linked mapping) and
        // CRYPTOGRAPHIC_ERASURE (the deletion record). Verify the CRYPTOGRAPHIC_ERASURE
        // entry is one of them and carries the expected fields.
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository, atLeastOnce()).save(captor.capture());

        AuditLogEntity erasureLog = captor.getAllValues().stream()
                .filter(e -> "CRYPTOGRAPHIC_ERASURE".equals(e.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CRYPTOGRAPHIC_ERASURE audit event not appended"));
        assertEquals("User", erasureLog.getEntityType());
        assertEquals(42L, erasureLog.getEntityId());
        assertEquals(1L, erasureLog.getActorPlaceholderId());
    }

    @Test
    void runUserDeletion_closedDisputes_areAnonymizedToPlaceholderBeforeUserDelete() {
        // Regression: prior to V5 the dispute FK silently blocked deletion for any user
        // who had ever filed a dispute. The fix anonymizes RESOLVED/DISMISSED disputes
        // by linking them to the placeholder and nulling user_id BEFORE the user delete
        // call, preserving forensic history without retaining PII linkage.
        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(List.of(disabledUser));
        when(disputeRepository.existsByUserIdAndStatus(42L, com.campusstore.core.domain.model.DisputeStatus.OPEN)).thenReturn(false);

        Page<AuditLogEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditLogRepository.findByActorUserId(eq(42L), any(Pageable.class))).thenReturn(emptyPage);

        when(deletedUserPlaceholderRepository.save(any(DeletedUserPlaceholderEntity.class)))
                .thenAnswer(inv -> {
                    DeletedUserPlaceholderEntity p = inv.getArgument(0);
                    p.setId(7L);
                    return p;
                });
        when(auditLogRepository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(disputeRepository.anonymizeForUser(eq(42L), eq(7L))).thenReturn(2);

        auditService.runUserDeletion();

        // Anonymization MUST happen, and MUST happen before the user is hard-deleted.
        org.mockito.InOrder order = inOrder(disputeRepository, userRepository);
        order.verify(disputeRepository).anonymizeForUser(42L, 7L);
        order.verify(userRepository).delete(disabledUser);
    }

    @Test
    void runUserDeletion_openDispute_blocksUserDeletion() {
        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(List.of(disabledUser));
        when(disputeRepository.existsByUserIdAndStatus(42L, com.campusstore.core.domain.model.DisputeStatus.OPEN)).thenReturn(true);

        auditService.runUserDeletion();

        // Verify no placeholder created and no user deleted
        verify(deletedUserPlaceholderRepository, never()).save(any());
        verify(userRepository, never()).delete(any(UserEntity.class));
    }

    @Test
    void runUserDeletion_noEligibleUsers_doesNothing() {
        when(userRepository.findUsersEligibleForDeletion(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        auditService.runUserDeletion();

        verify(deletedUserPlaceholderRepository, never()).save(any());
        verify(userRepository, never()).delete(any(UserEntity.class));
    }
}
