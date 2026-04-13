package com.campusstore.unit.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.BusinessException;
import com.campusstore.core.domain.model.PickStatus;
import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.NotificationService;
import com.campusstore.core.service.RequestService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.ItemRequestRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests request workflow state transitions including auto-approval,
 * self-approval blocking, invalid transitions, and quantity management.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RequestServiceWorkflowTest {

    @Mock private ItemRequestRepository itemRequestRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PickTaskRepository pickTaskRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    @InjectMocks
    private RequestService requestService;

    private UserEntity requester;
    private InventoryItemEntity item;

    @BeforeEach
    void setUp() {
        requester = new UserEntity();
        requester.setId(1L);
        requester.setUsername("student1");
        requester.setDepartmentId(10L);

        item = new InventoryItemEntity();
        item.setId(100L);
        item.setName("Lab Equipment");
        item.setIsActive(true);
        item.setQuantityAvailable(10);
        item.setDepartmentId(10L);
        // pick_task.location_id is NOT NULL in the schema, so items that reach the
        // approval path must have a storage location assigned.
        StorageLocationEntity loc = new StorageLocationEntity();
        loc.setId(500L);
        loc.setName("Shelf A1");
        item.setLocation(loc);
    }

    // --- Auto-approval flow (requiresApproval=false) ---

    @Test
    void createRequest_autoApproval_whenRequiresApprovalFalse() {
        item.setRequiresApproval(false);

        when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRequestRepository.findByRequesterIdAndItemIdAndStatusIn(eq(1L), eq(100L), anyList()))
                .thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(itemRequestRepository.save(any(ItemRequestEntity.class))).thenAnswer(inv -> {
            ItemRequestEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });
        when(inventoryItemRepository.save(any(InventoryItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemRequestEntity result = requestService.createRequest(1L, 100L, 2, "Need for lab");

        assertEquals(RequestStatus.AUTO_APPROVED, result.getStatus());
        assertNotNull(result.getApprovedAt());
    }

    @Test
    void createRequest_autoApproval_decrementsQuantity() {
        item.setRequiresApproval(false);
        item.setQuantityAvailable(10);

        when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRequestRepository.findByRequesterIdAndItemIdAndStatusIn(eq(1L), eq(100L), anyList()))
                .thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(itemRequestRepository.save(any(ItemRequestEntity.class))).thenAnswer(inv -> {
            ItemRequestEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });
        when(inventoryItemRepository.save(any(InventoryItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        requestService.createRequest(1L, 100L, 3, "Need for lab");

        ArgumentCaptor<InventoryItemEntity> captor = ArgumentCaptor.forClass(InventoryItemEntity.class);
        verify(inventoryItemRepository).save(captor.capture());
        assertEquals(7, captor.getValue().getQuantityAvailable());
    }

    @Test
    void createRequest_autoApproval_createsPickTask() {
        item.setRequiresApproval(false);

        when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRequestRepository.findByRequesterIdAndItemIdAndStatusIn(eq(1L), eq(100L), anyList()))
                .thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(itemRequestRepository.save(any(ItemRequestEntity.class))).thenAnswer(inv -> {
            ItemRequestEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });
        when(inventoryItemRepository.save(any(InventoryItemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        requestService.createRequest(1L, 100L, 1, "Need it");

        verify(pickTaskRepository).save(any(PickTaskEntity.class));
    }

    // --- Self-approval block detection ---

    @Test
    void createRequest_teacherFromOwnDepartment_setsPendingApproval() {
        item.setRequiresApproval(true);
        item.setDepartmentId(10L);
        requester.setDepartmentId(10L);

        when(inventoryItemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(itemRequestRepository.findByRequesterIdAndItemIdAndStatusIn(eq(1L), eq(100L), anyList()))
                .thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRoleRepository.existsByUserIdAndRole(1L, Role.TEACHER)).thenReturn(true);
        when(itemRequestRepository.save(any(ItemRequestEntity.class))).thenAnswer(inv -> {
            ItemRequestEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });

        ItemRequestEntity result = requestService.createRequest(1L, 100L, 1, "Self dept");

        assertEquals(RequestStatus.PENDING_APPROVAL, result.getStatus());
    }

    // --- Invalid state transitions ---

    @Test
    void approveRequest_alreadyRejected_throwsBusinessException() {
        ItemRequestEntity rejected = new ItemRequestEntity();
        rejected.setId(1L);
        rejected.setRequesterId(1L);
        rejected.setStatus(RequestStatus.REJECTED);
        rejected.setItem(item);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(rejected));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> requestService.approveRequest(1L, 50L));
        assertTrue(ex.getMessage().contains("not pending approval"));
    }

    @Test
    void approveRequest_alreadyApproved_throwsBusinessException() {
        ItemRequestEntity approved = new ItemRequestEntity();
        approved.setId(1L);
        approved.setRequesterId(1L);
        approved.setStatus(RequestStatus.APPROVED);
        approved.setItem(item);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(approved));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> requestService.approveRequest(1L, 50L));
        assertTrue(ex.getMessage().contains("not pending approval"));
    }

    @Test
    void approveRequest_alreadyCancelled_throwsBusinessException() {
        ItemRequestEntity cancelled = new ItemRequestEntity();
        cancelled.setId(1L);
        cancelled.setRequesterId(1L);
        cancelled.setStatus(RequestStatus.CANCELLED);
        cancelled.setItem(item);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(cancelled));

        assertThrows(BusinessException.class,
                () -> requestService.approveRequest(1L, 50L));
    }

    @Test
    void rejectRequest_alreadyRejected_throwsBusinessException() {
        ItemRequestEntity rejected = new ItemRequestEntity();
        rejected.setId(2L);
        rejected.setRequesterId(1L);
        rejected.setStatus(RequestStatus.REJECTED);

        when(itemRequestRepository.findById(2L)).thenReturn(Optional.of(rejected));

        assertThrows(BusinessException.class,
                () -> requestService.rejectRequest(2L, 50L, "reason"));
    }

    @Test
    void cancelRequest_terminalPickedUp_throwsBusinessException() {
        ItemRequestEntity pickedUp = new ItemRequestEntity();
        pickedUp.setId(3L);
        pickedUp.setRequesterId(1L);
        pickedUp.setStatus(RequestStatus.PICKED_UP);

        when(itemRequestRepository.findById(3L)).thenReturn(Optional.of(pickedUp));

        assertThrows(BusinessException.class,
                () -> requestService.cancelRequest(3L, 1L));
    }

    // --- Quantity decrement on approval ---

    @Test
    void approveRequest_decrementsItemQuantity() {
        ItemRequestEntity pending = new ItemRequestEntity();
        pending.setId(1L);
        pending.setRequesterId(1L);
        pending.setStatus(RequestStatus.PENDING_APPROVAL);
        pending.setQuantity(3);
        pending.setItem(item);

        item.setQuantityAvailable(10);

        Long approverId = 50L;
        UserEntity approver = new UserEntity();
        approver.setId(approverId);
        approver.setDepartmentId(10L);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(userRoleRepository.existsByUserIdAndRole(approverId, Role.TEACHER)).thenReturn(true);
        when(userRoleRepository.existsByUserIdAndRole(approverId, Role.ADMIN)).thenReturn(false);
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));
        when(itemRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        requestService.approveRequest(1L, approverId);

        ArgumentCaptor<InventoryItemEntity> captor = ArgumentCaptor.forClass(InventoryItemEntity.class);
        verify(inventoryItemRepository).save(captor.capture());
        assertEquals(7, captor.getValue().getQuantityAvailable());
    }

    // --- Quantity increment on cancellation ---

    @Test
    void cancelRequest_fromApproved_incrementsQuantity() {
        ItemRequestEntity approved = new ItemRequestEntity();
        approved.setId(1L);
        approved.setRequesterId(1L);
        approved.setStatus(RequestStatus.APPROVED);
        approved.setQuantity(2);
        approved.setItem(item);

        item.setQuantityAvailable(8);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(approved));
        when(userRoleRepository.existsByUserIdAndRole(1L, Role.ADMIN)).thenReturn(false);
        when(itemRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickTaskRepository.findByRequestId(1L)).thenReturn(Collections.emptyList());

        requestService.cancelRequest(1L, 1L);

        ArgumentCaptor<InventoryItemEntity> captor = ArgumentCaptor.forClass(InventoryItemEntity.class);
        verify(inventoryItemRepository).save(captor.capture());
        assertEquals(10, captor.getValue().getQuantityAvailable());
    }

    @Test
    void cancelRequest_fromAutoApproved_incrementsQuantity() {
        ItemRequestEntity autoApproved = new ItemRequestEntity();
        autoApproved.setId(1L);
        autoApproved.setRequesterId(1L);
        autoApproved.setStatus(RequestStatus.AUTO_APPROVED);
        autoApproved.setQuantity(5);
        autoApproved.setItem(item);

        item.setQuantityAvailable(5);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(autoApproved));
        when(userRoleRepository.existsByUserIdAndRole(1L, Role.ADMIN)).thenReturn(false);
        when(itemRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickTaskRepository.findByRequestId(1L)).thenReturn(Collections.emptyList());

        requestService.cancelRequest(1L, 1L);

        ArgumentCaptor<InventoryItemEntity> captor = ArgumentCaptor.forClass(InventoryItemEntity.class);
        verify(inventoryItemRepository).save(captor.capture());
        assertEquals(10, captor.getValue().getQuantityAvailable());
    }

    @Test
    void cancelRequest_fromPendingApproval_doesNotIncrementQuantity() {
        ItemRequestEntity pending = new ItemRequestEntity();
        pending.setId(1L);
        pending.setRequesterId(1L);
        pending.setStatus(RequestStatus.PENDING_APPROVAL);
        pending.setQuantity(2);
        pending.setItem(item);

        item.setQuantityAvailable(10);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(userRoleRepository.existsByUserIdAndRole(1L, Role.ADMIN)).thenReturn(false);
        when(itemRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickTaskRepository.findByRequestId(1L)).thenReturn(Collections.emptyList());

        requestService.cancelRequest(1L, 1L);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void cancelRequest_setsStatusToCancelled() {
        ItemRequestEntity pending = new ItemRequestEntity();
        pending.setId(1L);
        pending.setRequesterId(1L);
        pending.setStatus(RequestStatus.PENDING_APPROVAL);
        pending.setQuantity(1);
        pending.setItem(item);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(userRoleRepository.existsByUserIdAndRole(1L, Role.ADMIN)).thenReturn(false);
        when(itemRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickTaskRepository.findByRequestId(1L)).thenReturn(Collections.emptyList());

        ItemRequestEntity result = requestService.cancelRequest(1L, 1L);

        assertEquals(RequestStatus.CANCELLED, result.getStatus());
    }
}
