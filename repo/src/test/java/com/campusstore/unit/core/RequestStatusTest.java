package com.campusstore.unit.core;

import com.campusstore.core.domain.model.RequestStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class RequestStatusTest {

    @Test
    void pendingApproval_canTransitionToApproved() {
        assertTrue(RequestStatus.PENDING_APPROVAL.canTransitionTo(RequestStatus.APPROVED));
    }

    @Test
    void pendingApproval_canTransitionToRejected() {
        assertTrue(RequestStatus.PENDING_APPROVAL.canTransitionTo(RequestStatus.REJECTED));
    }

    @Test
    void pendingApproval_canTransitionToCancelled() {
        assertTrue(RequestStatus.PENDING_APPROVAL.canTransitionTo(RequestStatus.CANCELLED));
    }

    @Test
    void pendingApproval_cannotTransitionToPickedUp() {
        assertFalse(RequestStatus.PENDING_APPROVAL.canTransitionTo(RequestStatus.PICKED_UP));
    }

    @Test
    void approved_canTransitionToPicking() {
        assertTrue(RequestStatus.APPROVED.canTransitionTo(RequestStatus.PICKING));
    }

    @Test
    void approved_canTransitionToCancelled() {
        assertTrue(RequestStatus.APPROVED.canTransitionTo(RequestStatus.CANCELLED));
    }

    @Test
    void approved_cannotTransitionToRejected() {
        assertFalse(RequestStatus.APPROVED.canTransitionTo(RequestStatus.REJECTED));
    }

    @Test
    void autoApproved_canTransitionToPicking() {
        assertTrue(RequestStatus.AUTO_APPROVED.canTransitionTo(RequestStatus.PICKING));
    }

    @Test
    void picking_canTransitionToReadyForPickup() {
        assertTrue(RequestStatus.PICKING.canTransitionTo(RequestStatus.READY_FOR_PICKUP));
    }

    @Test
    void readyForPickup_canTransitionToPickedUp() {
        assertTrue(RequestStatus.READY_FOR_PICKUP.canTransitionTo(RequestStatus.PICKED_UP));
    }

    @Test
    void readyForPickup_canTransitionToOverdue() {
        assertTrue(RequestStatus.READY_FOR_PICKUP.canTransitionTo(RequestStatus.OVERDUE));
    }

    @Test
    void overdue_canTransitionToPickedUp() {
        assertTrue(RequestStatus.OVERDUE.canTransitionTo(RequestStatus.PICKED_UP));
    }

    @Test
    void overdue_canTransitionToCancelled() {
        assertTrue(RequestStatus.OVERDUE.canTransitionTo(RequestStatus.CANCELLED));
    }

    @Test
    void pickedUp_isTerminal() {
        assertTrue(RequestStatus.PICKED_UP.isTerminal());
        assertFalse(RequestStatus.PICKED_UP.canTransitionTo(RequestStatus.CANCELLED));
    }

    @Test
    void rejected_isTerminal() {
        assertTrue(RequestStatus.REJECTED.isTerminal());
        assertFalse(RequestStatus.REJECTED.canTransitionTo(RequestStatus.APPROVED));
    }

    @Test
    void cancelled_isTerminal_noTransitions() {
        assertFalse(RequestStatus.CANCELLED.canTransitionTo(RequestStatus.APPROVED));
        assertFalse(RequestStatus.CANCELLED.canTransitionTo(RequestStatus.PICKED_UP));
    }

    @Test
    void pendingApproval_isCancellable() {
        assertTrue(RequestStatus.PENDING_APPROVAL.isCancellable());
    }

    @Test
    void approved_isCancellable() {
        assertTrue(RequestStatus.APPROVED.isCancellable());
    }

    @Test
    void pickedUp_isNotCancellable() {
        assertFalse(RequestStatus.PICKED_UP.isCancellable());
    }

    @Test
    void rejected_isNotCancellable() {
        assertFalse(RequestStatus.REJECTED.isCancellable());
    }
}
