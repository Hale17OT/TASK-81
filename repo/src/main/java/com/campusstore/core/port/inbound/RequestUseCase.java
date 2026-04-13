package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.RequestStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Inbound port for item-request lifecycle operations.
 */
public interface RequestUseCase {

    /**
     * Create a new item request.
     *
     * @param requesterId   the requesting user's id
     * @param itemId        the item id
     * @param quantity      requested quantity
     * @param justification reason for the request
     * @return the id of the created request
     */
    Long createRequest(Long requesterId, Long itemId, int quantity, String justification);

    /**
     * Approve a pending request.
     *
     * @param requestId  the request id
     * @param approverId the approving user's id
     */
    void approveRequest(Long requestId, Long approverId);

    /**
     * Reject a pending request.
     *
     * @param requestId  the request id
     * @param approverId the rejecting user's id
     * @param reason     the rejection reason
     */
    void rejectRequest(Long requestId, Long approverId, String reason);

    /**
     * Cancel a request (by the requester).
     *
     * @param requestId the request id
     * @param userId    the user requesting cancellation
     */
    void cancelRequest(Long requestId, Long userId);

    /**
     * Mark a request as picked up.
     *
     * @param requestId the request id
     * @param staffId   the staff member confirming pickup
     */
    void markPickedUp(Long requestId, Long staffId);

    /**
     * Get the details of a single request.
     *
     * @param requestId the request id
     * @param viewerId  the user viewing the request (for access control)
     * @return the request view
     */
    RequestView getRequest(Long requestId, Long viewerId);

    /**
     * List requests created by a specific user.
     *
     * @param userId   the requesting user's id
     * @param pageable pagination information
     * @return a page of request views
     */
    Page<RequestView> listMyRequests(Long userId, Pageable pageable);

    /**
     * List requests pending approval by a specific teacher.
     *
     * @param teacherId the teacher's user id
     * @param pageable  pagination information
     * @return a page of request views
     */
    Page<RequestView> listPendingApprovals(Long teacherId, Pageable pageable);

    /**
     * List all requests (admin view).
     *
     * @param pageable pagination information
     * @return a page of request views
     */
    Page<RequestView> listAllRequests(Pageable pageable);

    // ── View types ─────────────────────────────────────────────────────

    record RequestView(
            Long id,
            Long requesterId,
            String requesterName,
            Long itemId,
            String itemName,
            int quantity,
            RequestStatus status,
            String justification,
            String rejectionReason,
            Long approverId,
            String approverName,
            Instant approvedAt,
            Instant pickedUpAt,
            Instant pickupDeadline,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
