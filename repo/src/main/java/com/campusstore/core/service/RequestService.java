package com.campusstore.core.service;

import com.campusstore.core.domain.event.AccessDeniedException;
import com.campusstore.core.domain.event.BusinessException;
import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.NotificationType;
import com.campusstore.core.domain.model.PickStatus;
import com.campusstore.core.domain.model.RequestStatus;
import com.campusstore.core.domain.model.Role;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import com.campusstore.infrastructure.persistence.entity.UserEntity;
import com.campusstore.infrastructure.persistence.entity.UserRoleEntity;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.ItemRequestRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.UserRepository;
import com.campusstore.infrastructure.persistence.repository.UserRoleRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    private final ItemRequestRepository itemRequestRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PickTaskRepository pickTaskRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public RequestService(ItemRequestRepository itemRequestRepository,
                          InventoryItemRepository inventoryItemRepository,
                          UserRepository userRepository,
                          UserRoleRepository userRoleRepository,
                          PickTaskRepository pickTaskRepository,
                          NotificationService notificationService,
                          AuditService auditService) {
        this.itemRequestRepository = itemRequestRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.pickTaskRepository = pickTaskRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public ItemRequestEntity createRequest(Long requesterId, Long itemId, Integer quantity, String justification) {
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        Objects.requireNonNull(itemId, "Item ID must not be null");
        Objects.requireNonNull(quantity, "Quantity must not be null");

        if (quantity <= 0) {
            throw new BusinessException("Quantity must be positive");
        }

        // Validate item exists and is active
        InventoryItemEntity item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemId));

        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new BusinessException("Item is not active");
        }

        // Check quantity available
        int available = item.getQuantityAvailable() != null ? item.getQuantityAvailable() : 0;
        if (available < quantity) {
            throw new BusinessException("Insufficient quantity available. Requested: " + quantity + ", Available: " + available);
        }

        // Check for duplicate pending request (same user + item + pending status)
        List<ItemRequestEntity> duplicates = itemRequestRepository.findByRequesterIdAndItemIdAndStatusIn(
                requesterId, itemId, List.of(RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED,
                        RequestStatus.AUTO_APPROVED, RequestStatus.PICKING));
        if (!duplicates.isEmpty()) {
            throw new ConflictException("A pending request already exists for this item");
        }

        UserEntity requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));

        ItemRequestEntity request = new ItemRequestEntity();
        request.setRequester(requester);
        request.setItem(item);
        request.setQuantity(quantity);
        request.setJustification(justification);
        request.setCreatedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        if (!Boolean.TRUE.equals(item.getRequiresApproval())) {
            // Auto-approve
            request.setStatus(RequestStatus.AUTO_APPROVED);
            request.setApprovedAt(Instant.now());

            // Decrement quantity
            item.setQuantityAvailable(available - quantity);
            item.setUpdatedAt(Instant.now());
            inventoryItemRepository.save(item);

            // Set pickup deadline for auto-approved requests
            request.setPickupDeadline(Instant.now().plus(7, ChronoUnit.DAYS));

            ItemRequestEntity saved = itemRequestRepository.save(request);

            // Create pick task
            createPickTask(saved);

            // Send notification to requester
            notificationService.sendNotification(requesterId, NotificationType.REQUEST_APPROVED,
                    "Request Auto-Approved",
                    "Your request for " + item.getName() + " has been auto-approved.",
                    false, "ItemRequest", saved.getId());

            auditService.log(requesterId, "CREATE_REQUEST_AUTO_APPROVED", "ItemRequest", saved.getId(),
                    "{\"itemId\":" + itemId + ",\"quantity\":" + quantity + "}");
            log.info("Request created and auto-approved id={}", saved.getId());
            return saved;
        }

        // Check self-approval block for teachers requesting from their own department
        boolean isRequesterTeacher = hasRole(requesterId, Role.TEACHER);
        boolean isSameDepartment = requester.getDepartmentId() != null
                && requester.getDepartmentId().equals(item.getDepartmentId());

        if (isRequesterTeacher && isSameDepartment) {
            // Self-approval block: route to peer teacher or admin
            request.setStatus(RequestStatus.PENDING_APPROVAL);
            log.info("Self-approval block: teacher requesting from own department, routing to peer/admin");
        } else {
            request.setStatus(RequestStatus.PENDING_APPROVAL);
        }

        // Explicit-assignment approval model: at creation time we pick a specific
        // approver (a teacher in the item's department other than the requester, or
        // any admin as fallback). Least-privilege visibility then flows naturally —
        // only the assigned approver (plus requester/assigned pick staff/admin) can
        // see the request.
        Long itemDeptId = item.getDepartment() != null
                ? item.getDepartment().getId()
                : item.getDepartmentId();
        UserEntity assignedApprover = findAssignedApprover(itemDeptId, requesterId);
        if (assignedApprover != null) {
            request.setApprover(assignedApprover);
        }

        request.setPickupDeadline(Instant.now().plus(7, ChronoUnit.DAYS));
        ItemRequestEntity saved = itemRequestRepository.save(request);

        // Send notification about submitted request
        notificationService.sendNotification(requesterId, NotificationType.REQUEST_SUBMITTED,
                "Request Submitted",
                "Your request for " + item.getName() + " is pending approval.",
                false, "ItemRequest", saved.getId());

        auditService.log(requesterId, "CREATE_REQUEST", "ItemRequest", saved.getId(),
                "{\"itemId\":" + itemId + ",\"quantity\":" + quantity + ",\"status\":\"PENDING_APPROVAL\"}");
        log.info("Request created id={}, status=PENDING_APPROVAL", saved.getId());
        return saved;
    }

    public ItemRequestEntity approveRequest(Long requestId, Long approverId) {
        Objects.requireNonNull(requestId, "Request ID must not be null");
        Objects.requireNonNull(approverId, "Approver ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        // Validate PENDING_APPROVAL status
        if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw new BusinessException("Request is not pending approval. Current status: " + request.getStatus());
        }

        // Validate approver has TEACHER or ADMIN role
        boolean isTeacher = hasRole(approverId, Role.TEACHER);
        boolean isAdmin = hasRole(approverId, Role.ADMIN);
        if (!isTeacher && !isAdmin) {
            throw new AccessDeniedException("Approver must have TEACHER or ADMIN role");
        }

        // If approver is TEACHER, enforce strict assignment: only the assigned approver
        // (set at creation time) may act. Department check is retained as a redundant
        // defense in depth for historical requests created before the assignment model.
        if (isTeacher && !isAdmin) {
            UserEntity approver = userRepository.findById(approverId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", approverId));
            InventoryItemEntity item = request.getItem();
            if (item.getDepartmentId() == null || !item.getDepartmentId().equals(approver.getDepartmentId())) {
                throw new AccessDeniedException("Teacher can only approve requests for items in their department");
            }
            Long assignedApproverId = request.getApprover() != null
                    ? request.getApprover().getId()
                    : request.getApproverId();
            if (assignedApproverId != null && !approverId.equals(assignedApproverId)) {
                throw new AccessDeniedException("Only the assigned approver may act on this request");
            }
        }

        // Self-approval block
        if (approverId.equals(request.getRequesterId())) {
            throw new AccessDeniedException("Cannot approve your own request");
        }

        // Set status
        UserEntity approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", approverId));
        request.setStatus(RequestStatus.APPROVED);
        request.setApprover(approver);
        request.setApprovedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        if (request.getPickupDeadline() == null) {
            request.setPickupDeadline(Instant.now().plus(7, ChronoUnit.DAYS));
        }

        // Decrement item quantity
        InventoryItemEntity item = request.getItem();
        int available = item.getQuantityAvailable() != null ? item.getQuantityAvailable() : 0;
        int qty = request.getQuantity() != null ? request.getQuantity() : 1;
        if (available < qty) {
            throw new BusinessException("Insufficient quantity available for approval");
        }
        item.setQuantityAvailable(available - qty);
        item.setUpdatedAt(Instant.now());
        inventoryItemRepository.save(item);

        ItemRequestEntity saved = itemRequestRepository.save(request);

        // Create pick task
        createPickTask(saved);

        // Send notification to requester
        notificationService.sendNotification(request.getRequesterId(), NotificationType.REQUEST_APPROVED,
                "Request Approved",
                "Your request for " + item.getName() + " has been approved.",
                false, "ItemRequest", saved.getId());

        auditService.log(approverId, "APPROVE_REQUEST", "ItemRequest", requestId,
                "{\"requesterId\":" + request.getRequesterId() + "}");
        log.info("Request id={} approved by user={}", requestId, approverId);
        return saved;
    }

    public ItemRequestEntity rejectRequest(Long requestId, Long approverId, String rejectionReason) {
        Objects.requireNonNull(requestId, "Request ID must not be null");
        Objects.requireNonNull(approverId, "Approver ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        // Validate PENDING_APPROVAL status
        if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
            throw new BusinessException("Request is not pending approval. Current status: " + request.getStatus());
        }

        // Validate approver permissions
        boolean isTeacher = hasRole(approverId, Role.TEACHER);
        boolean isAdmin = hasRole(approverId, Role.ADMIN);
        if (!isTeacher && !isAdmin) {
            throw new AccessDeniedException("Approver must have TEACHER or ADMIN role");
        }

        if (isTeacher && !isAdmin) {
            UserEntity approver = userRepository.findById(approverId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", approverId));
            InventoryItemEntity item = request.getItem();
            if (item.getDepartmentId() == null || !item.getDepartmentId().equals(approver.getDepartmentId())) {
                throw new AccessDeniedException("Teacher can only reject requests for items in their department");
            }
            Long assignedApproverId = request.getApprover() != null
                    ? request.getApprover().getId()
                    : request.getApproverId();
            if (assignedApproverId != null && !approverId.equals(assignedApproverId)) {
                throw new AccessDeniedException("Only the assigned approver may act on this request");
            }
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(rejectionReason);
        request.setUpdatedAt(Instant.now());

        UserEntity approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", approverId));
        request.setApprover(approver);

        ItemRequestEntity saved = itemRequestRepository.save(request);

        // Send notification to requester
        notificationService.sendNotification(request.getRequesterId(), NotificationType.REQUEST_REJECTED,
                "Request Rejected",
                "Your request has been rejected." + (rejectionReason != null ? " Reason: " + rejectionReason : ""),
                false, "ItemRequest", saved.getId());

        auditService.log(approverId, "REJECT_REQUEST", "ItemRequest", requestId,
                "{\"reason\":\"" + (rejectionReason != null ? rejectionReason.replace("\"", "\\\"") : "") + "\"}");
        log.info("Request id={} rejected by user={}", requestId, approverId);
        return saved;
    }

    public ItemRequestEntity cancelRequest(Long requestId, Long cancelledByUserId) {
        Objects.requireNonNull(requestId, "Request ID must not be null");
        Objects.requireNonNull(cancelledByUserId, "User ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        // Validate cancellable status
        if (!request.getStatus().isCancellable()) {
            throw new BusinessException("Request cannot be cancelled in status: " + request.getStatus());
        }

        // Validate caller is requester or ADMIN
        boolean isRequester = cancelledByUserId.equals(request.getRequesterId());
        boolean isAdmin = hasRole(cancelledByUserId, Role.ADMIN);
        if (!isRequester && !isAdmin) {
            throw new AccessDeniedException("Only the requester or an admin can cancel this request");
        }

        // Capture previous status before mutation for audit logging
        RequestStatus previousStatus = request.getStatus();

        // If was APPROVED or AUTO_APPROVED: increment quantity_available
        if (previousStatus == RequestStatus.APPROVED
                || previousStatus == RequestStatus.AUTO_APPROVED
                || previousStatus == RequestStatus.PICKING
                || previousStatus == RequestStatus.READY_FOR_PICKUP
                || previousStatus == RequestStatus.OVERDUE) {
            InventoryItemEntity item = request.getItem();
            int available = item.getQuantityAvailable() != null ? item.getQuantityAvailable() : 0;
            int qty = request.getQuantity() != null ? request.getQuantity() : 1;
            item.setQuantityAvailable(available + qty);
            item.setUpdatedAt(Instant.now());
            inventoryItemRepository.save(item);
        }

        request.setStatus(RequestStatus.CANCELLED);
        request.setUpdatedAt(Instant.now());
        ItemRequestEntity saved = itemRequestRepository.save(request);

        // Cancel any pending pick tasks
        List<PickTaskEntity> pickTasks = pickTaskRepository.findByRequestId(requestId);
        for (PickTaskEntity pickTask : pickTasks) {
            if (pickTask.getStatus() == PickStatus.PENDING || pickTask.getStatus() == PickStatus.IN_PROGRESS) {
                pickTask.setStatus(PickStatus.CANCELLED);
                pickTaskRepository.save(pickTask);
            }
        }

        auditService.log(cancelledByUserId, "CANCEL_REQUEST", "ItemRequest", requestId,
                "{\"previousStatus\":\"" + previousStatus + "\"}");
        log.info("Request id={} cancelled by user={}", requestId, cancelledByUserId);
        return saved;
    }

    /**
     * Warehouse staff begins picking the items for an approved request. Transitions the
     * request from APPROVED/AUTO_APPROVED to PICKING and marks the associated pick task(s)
     * IN_PROGRESS. Required for the missed-checkin scheduler to detect stalled picks.
     */
    public ItemRequestEntity startPicking(Long requestId, Long staffUserId) {
        Objects.requireNonNull(requestId, "Request ID must not be null");
        Objects.requireNonNull(staffUserId, "Staff user ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        if (request.getStatus() != RequestStatus.APPROVED
                && request.getStatus() != RequestStatus.AUTO_APPROVED) {
            throw new BusinessException("Request cannot be picked. Current status: " + request.getStatus());
        }

        request.setStatus(RequestStatus.PICKING);
        request.setUpdatedAt(Instant.now());
        ItemRequestEntity saved = itemRequestRepository.save(request);

        for (var pt : pickTaskRepository.findByRequestId(requestId)) {
            if (pt.getStatus() == PickStatus.PENDING) {
                pt.setStatus(PickStatus.IN_PROGRESS);
                pt.setStartedAt(Instant.now());
                pickTaskRepository.save(pt);
            }
        }

        auditService.log(staffUserId, "START_PICKING", "ItemRequest", requestId, "{}");
        log.info("Request id={} transitioned to PICKING by staff={}", requestId, staffUserId);
        return saved;
    }

    /**
     * Warehouse staff finishes picking — items are staged for pickup. Transitions
     * PICKING to READY_FOR_PICKUP and marks pick tasks COMPLETED. The PICKED_UP
     * transition is the next step (handled by markPickedUp).
     */
    public ItemRequestEntity markReadyForPickup(Long requestId, Long staffUserId) {
        Objects.requireNonNull(requestId, "Request ID must not be null");
        Objects.requireNonNull(staffUserId, "Staff user ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        if (request.getStatus() != RequestStatus.PICKING) {
            throw new BusinessException("Request is not being picked. Current status: " + request.getStatus());
        }

        request.setStatus(RequestStatus.READY_FOR_PICKUP);
        request.setUpdatedAt(Instant.now());
        ItemRequestEntity saved = itemRequestRepository.save(request);

        for (var pt : pickTaskRepository.findByRequestId(requestId)) {
            if (pt.getStatus() == PickStatus.IN_PROGRESS || pt.getStatus() == PickStatus.PENDING) {
                pt.setStatus(PickStatus.COMPLETED);
                pt.setCompletedAt(Instant.now());
                pickTaskRepository.save(pt);
            }
        }

        // Notify requester their items are staged.
        notificationService.sendNotification(request.getRequesterId(), NotificationType.PICKUP_READY,
                "Items Ready for Pickup",
                "Your requested items are staged and ready for pickup.",
                false, "ItemRequest", saved.getId());

        auditService.log(staffUserId, "READY_FOR_PICKUP", "ItemRequest", requestId, "{}");
        log.info("Request id={} transitioned to READY_FOR_PICKUP by staff={}", requestId, staffUserId);
        return saved;
    }

    public ItemRequestEntity markPickedUp(Long requestId, Long userId) {
        Objects.requireNonNull(requestId, "Request ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        // Validate READY_FOR_PICKUP or OVERDUE status
        if (request.getStatus() != RequestStatus.READY_FOR_PICKUP
                && request.getStatus() != RequestStatus.OVERDUE) {
            throw new BusinessException("Request is not ready for pickup. Current status: " + request.getStatus());
        }

        request.setStatus(RequestStatus.PICKED_UP);
        request.setPickedUpAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        ItemRequestEntity saved = itemRequestRepository.save(request);

        // Send notification
        notificationService.sendNotification(request.getRequesterId(), NotificationType.PICKUP_READY,
                "Item Picked Up",
                "Your item has been picked up successfully.",
                false, "ItemRequest", saved.getId());

        auditService.log(userId, "MARK_PICKED_UP", "ItemRequest", requestId, "{}");
        log.info("Request id={} marked as picked up", requestId);
        return saved;
    }

    @Transactional(readOnly = true)
    public ItemRequestEntity getRequest(Long requestId, Long userId) {
        Objects.requireNonNull(requestId, "Request ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");

        ItemRequestEntity request = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ItemRequest", requestId));

        // Object-level auth: strict least-privilege visibility —
        // only requester, assigned pick staff, the actual approver, and admins.
        // Department teachers approve/reject from the pending-approvals list;
        // they do NOT get detail access to requests they have not claimed.
        boolean isRequester = userId.equals(request.getRequesterId());
        // Scalar approver_id can be null mid-transaction (insertable=false on the column);
        // prefer the @ManyToOne relationship when present.
        Long resolvedApproverId = request.getApprover() != null
                ? request.getApprover().getId()
                : request.getApproverId();
        boolean isApprover = userId.equals(resolvedApproverId);
        boolean isAdmin = hasRole(userId, Role.ADMIN);

        // Check if viewer is assigned staff on any pick task for this request
        boolean isAssignedStaff = false;
        List<com.campusstore.infrastructure.persistence.entity.PickTaskEntity> pickTasks =
                pickTaskRepository.findByRequestId(requestId);
        for (var pt : pickTasks) {
            if (userId.equals(pt.getAssignedTo())) {
                isAssignedStaff = true;
                break;
            }
        }

        if (!isRequester && !isApprover && !isAdmin && !isAssignedStaff) {
            throw new AccessDeniedException("You do not have access to this request");
        }

        return request;
    }

    @Transactional(readOnly = true)
    public Page<ItemRequestEntity> listMyRequests(Long requesterId, Pageable pageable) {
        Objects.requireNonNull(requesterId, "Requester ID must not be null");
        return itemRequestRepository.findByRequesterId(requesterId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ItemRequestEntity> listPendingApprovals(Long userId, Pageable pageable) {
        Objects.requireNonNull(userId, "User ID must not be null");

        boolean isAdmin = hasRole(userId, Role.ADMIN);
        if (isAdmin) {
            return itemRequestRepository.findByStatus(RequestStatus.PENDING_APPROVAL, pageable);
        }

        boolean isTeacher = hasRole(userId, Role.TEACHER);
        if (isTeacher) {
            // Strict least-privilege: a teacher only sees pending requests that have been
            // explicitly assigned to them as the approver. This matches the getRequest
            // visibility rule and prevents browsing peers' pending queue.
            return itemRequestRepository.findByApproverIdAndStatus(
                    userId, RequestStatus.PENDING_APPROVAL, pageable);
        }

        throw new AccessDeniedException("Only teachers and admins can view pending approvals");
    }

    /**
     * Pick the approver for a newly-submitted pending request. Preference:
     * <ol>
     *   <li>A TEACHER in the same department as the item, other than the requester,
     *       chosen deterministically by lowest user id for reproducibility.</li>
     *   <li>Otherwise the first ADMIN found.</li>
     *   <li>Otherwise {@code null} — the request stays unassigned and only admins will see it.</li>
     * </ol>
     */
    private UserEntity findAssignedApprover(Long itemDepartmentId, Long requesterId) {
        if (itemDepartmentId != null) {
            UserEntity bestTeacher = null;
            for (UserRoleEntity role : userRoleRepository.findByRole(Role.TEACHER)) {
                UserEntity candidate = resolveRoleUser(role);
                if (candidate == null || candidate.getId() == null) continue;
                if (candidate.getId().equals(requesterId)) continue;
                if (candidate.getDepartment() == null
                        || !itemDepartmentId.equals(candidate.getDepartment().getId())) continue;
                if (candidate.getAccountStatus() != null
                        && !"ACTIVE".equals(candidate.getAccountStatus().name())) continue;
                if (bestTeacher == null || candidate.getId() < bestTeacher.getId()) {
                    bestTeacher = candidate;
                }
            }
            if (bestTeacher != null) return bestTeacher;
        }
        // Fallback: any admin. Admins already see every pending request in their list,
        // so leaving this empty is also acceptable; we still pick one to preserve the
        // "one designated approver" contract.
        for (UserRoleEntity role : userRoleRepository.findByRole(Role.ADMIN)) {
            UserEntity candidate = resolveRoleUser(role);
            if (candidate == null || candidate.getId() == null) continue;
            if (candidate.getId().equals(requesterId)) continue;
            if (candidate.getAccountStatus() != null
                    && !"ACTIVE".equals(candidate.getAccountStatus().name())) continue;
            return candidate;
        }
        return null;
    }

    /**
     * Resolve the user attached to a role row. The scalar {@code user_id} column is
     * {@code insertable=false/updatable=false}, so within a single transaction
     * {@code role.getUserId()} may be {@code null}. Prefer the managed relationship
     * and fall back to a repo lookup if Hibernate hasn't linked it yet.
     */
    private UserEntity resolveRoleUser(UserRoleEntity role) {
        if (role.getUser() != null) return role.getUser();
        if (role.getUserId() != null) {
            return userRepository.findById(role.getUserId()).orElse(null);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<ItemRequestEntity> listAllRequests(Long userId, Pageable pageable) {
        Objects.requireNonNull(userId, "User ID must not be null");

        if (!hasRole(userId, Role.ADMIN)) {
            throw new AccessDeniedException("Only admins can view all requests");
        }

        return itemRequestRepository.findAll(pageable);
    }

    private void createPickTask(ItemRequestEntity request) {
        InventoryItemEntity item = request.getItem();
        // Guard: schema enforces pick_task.location_id NOT NULL, so an item without a
        // storage location cannot be fulfilled. Refuse rather than silently emitting a
        // row that will blow up at DB flush and abort the whole approval transaction.
        if (item.getLocation() == null) {
            throw new BusinessException("Item '" + item.getName()
                    + "' has no storage location assigned; cannot generate pick task. "
                    + "Assign a location before approving.");
        }
        PickTaskEntity pickTask = new PickTaskEntity();
        pickTask.setRequest(request);
        pickTask.setLocation(item.getLocation());
        pickTask.setStatus(PickStatus.PENDING);
        pickTask.setSequenceOrder(1);
        pickTask.setCreatedAt(Instant.now());
        pickTaskRepository.save(pickTask);

        log.debug("Pick task created for request id={}", request.getId());
    }

    private boolean hasRole(Long userId, Role role) {
        return userRoleRepository.existsByUserIdAndRole(userId, role);
    }
}
