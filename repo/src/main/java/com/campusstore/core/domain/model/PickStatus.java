package com.campusstore.core.domain.model;

public enum PickStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(PickStatus target) {
        return switch (this) {
            case PENDING -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
