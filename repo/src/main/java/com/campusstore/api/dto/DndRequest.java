package com.campusstore.api.dto;

import jakarta.validation.constraints.AssertTrue;

import java.time.LocalTime;

/**
 * Payload for {@code PUT /api/user/preferences/dnd}.
 *
 * Semantics: both fields null clears DND. Otherwise both must be non-null and
 * not equal to each other (a zero-length window is meaningless). Wrap-around
 * windows (e.g., 22:00 → 07:00) are valid — {@code ProfileService.updateDnd}
 * simply stores the pair and downstream notification filtering handles the
 * wrap case.
 */
public class DndRequest {

    private LocalTime startTime;

    private LocalTime endTime;

    public DndRequest() {
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    @AssertTrue(message = "startTime and endTime must both be provided (or both omitted to clear DND)")
    public boolean isPairingValid() {
        return (startTime == null) == (endTime == null);
    }

    @AssertTrue(message = "startTime and endTime must differ")
    public boolean isWindowNonEmpty() {
        if (startTime == null || endTime == null) return true; // both null is allowed (clear)
        return !startTime.equals(endTime);
    }
}
