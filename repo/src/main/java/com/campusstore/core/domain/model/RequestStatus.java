package com.campusstore.core.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum RequestStatus {
    PENDING_APPROVAL,
    APPROVED,
    AUTO_APPROVED,
    REJECTED,
    PICKING,
    READY_FOR_PICKUP,
    PICKED_UP,
    CANCELLED,
    OVERDUE;

    private static final Set<RequestStatus> TERMINAL = EnumSet.of(PICKED_UP, REJECTED, CANCELLED);
    private static final Set<RequestStatus> CANCELLABLE = EnumSet.of(
            PENDING_APPROVAL, APPROVED, AUTO_APPROVED, PICKING, READY_FOR_PICKUP, OVERDUE
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }

    public boolean canTransitionTo(RequestStatus target) {
        return switch (this) {
            case PENDING_APPROVAL -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED -> target == PICKING || target == CANCELLED;
            case AUTO_APPROVED -> target == PICKING || target == CANCELLED;
            case PICKING -> target == READY_FOR_PICKUP || target == CANCELLED;
            case READY_FOR_PICKUP -> target == PICKED_UP || target == OVERDUE || target == CANCELLED;
            case OVERDUE -> target == PICKED_UP || target == CANCELLED;
            case REJECTED, PICKED_UP, CANCELLED -> false;
        };
    }
}
