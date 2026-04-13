package com.campusstore.unit.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.core.domain.model.Role;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.NotificationService;
import com.campusstore.core.service.RequestService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests object-level authorization rules in RequestService.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RequestServiceAuthorizationTest {

    @Mock private ItemRequestRepository itemRequestRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PickTaskRepository pickTaskRepository;
    @Mock private NotificationService notificationService;
    @Mock private AuditService auditService;

    @InjectMocks
    private RequestService requestService;

    private ItemRequestEntity request;
    private InventoryItemEntity item;

    @BeforeEach
    void setUp() {
        item = new InventoryItemEntity();
        item.setId(100L);
        item.setName("Textbook");
        item.setDepartmentId(10L);
        item.setRequiresApproval(true);

        request = new ItemRequestEntity();
        request.setId(1L);
        request.setRequesterId(1L);
        request.setStatus(RequestStatus.PENDING_APPROVAL);
        request.setItem(item);
        request.setQuantity(1);
    }

    // --- getRequest object-level authorization ---

    @Test
    void getRequest_studentA_cannotViewStudentB_request() {
        Long studentAId = 2L;
        Long studentBId = 1L; // requester
        request.setRequesterId(studentBId);
        request.setApproverId(null);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRoleRepository.existsByUserIdAndRole(studentAId, Role.ADMIN)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> requestService.getRequest(1L, studentAId));
    }

    @Test
    void getRequest_requesterCanViewOwnRequest() {
        Long requesterId = 1L;
        request.setRequesterId(requesterId);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));

        ItemRequestEntity result = requestService.getRequest(1L, requesterId);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getRequest_adminCanViewAnyRequest() {
        Long adminId = 99L;
        request.setRequesterId(1L);
        request.setApproverId(null);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRoleRepository.existsByUserIdAndRole(adminId, Role.ADMIN)).thenReturn(true);

        ItemRequestEntity result = requestService.getRequest(1L, adminId);
        assertNotNull(result);
    }

    // --- approveRequest: teacher cannot approve outside their department ---

    @Test
    void approveRequest_teacherOutsideDepartment_throwsAccessDenied() {
        Long teacherId = 50L;
        UserEntity teacher = new UserEntity();
        teacher.setId(teacherId);
        teacher.setDepartmentId(20L); // different department than item's 10L

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRoleRepository.existsByUserIdAndRole(teacherId, Role.TEACHER)).thenReturn(true);
        when(userRoleRepository.existsByUserIdAndRole(teacherId, Role.ADMIN)).thenReturn(false);
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> requestService.approveRequest(1L, teacherId));
        assertTrue(ex.getMessage().contains("department"));
    }

    // --- approveRequest: teacher cannot self-approve ---

    @Test
    void approveRequest_selfApproval_throwsAccessDenied() {
        Long teacherId = 1L; // same as requesterId
        request.setRequesterId(teacherId);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRoleRepository.existsByUserIdAndRole(teacherId, Role.TEACHER)).thenReturn(true);
        when(userRoleRepository.existsByUserIdAndRole(teacherId, Role.ADMIN)).thenReturn(true);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> requestService.approveRequest(1L, teacherId));
        assertTrue(ex.getMessage().contains("own request"));
    }

    // --- cancelRequest: non-owner non-admin cannot cancel ---

    @Test
    void cancelRequest_nonOwnerNonAdmin_throwsAccessDenied() {
        Long otherUserId = 999L;
        request.setRequesterId(1L);
        request.setStatus(RequestStatus.PENDING_APPROVAL);

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRoleRepository.existsByUserIdAndRole(otherUserId, Role.ADMIN)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> requestService.cancelRequest(1L, otherUserId));
    }

    // --- rejectRequest: teacher cannot reject outside department ---

    @Test
    void rejectRequest_teacherOutsideDepartment_throwsAccessDenied() {
        Long teacherId = 50L;
        UserEntity teacher = new UserEntity();
        teacher.setId(teacherId);
        teacher.setDepartmentId(20L); // different from item department 10L

        when(itemRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRoleRepository.existsByUserIdAndRole(teacherId, Role.TEACHER)).thenReturn(true);
        when(userRoleRepository.existsByUserIdAndRole(teacherId, Role.ADMIN)).thenReturn(false);
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));

        assertThrows(AccessDeniedException.class,
                () -> requestService.rejectRequest(1L, teacherId, "Not relevant"));
    }
}
