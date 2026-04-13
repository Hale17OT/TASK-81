package com.campusstore.unit.core;

import com.campusstore.core.domain.model.PickStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PickStatusTest {

    // ── Valid transitions from PENDING ──────────────────────────────────

    @Test
    void pending_canTransitionToInProgress() {
        assertThat(PickStatus.PENDING.canTransitionTo(PickStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void pending_canTransitionToCancelled() {
        assertThat(PickStatus.PENDING.canTransitionTo(PickStatus.CANCELLED)).isTrue();
    }

    @Test
    void pending_cannotTransitionToCompleted() {
        assertThat(PickStatus.PENDING.canTransitionTo(PickStatus.COMPLETED)).isFalse();
    }

    @Test
    void pending_cannotTransitionToSelf() {
        assertThat(PickStatus.PENDING.canTransitionTo(PickStatus.PENDING)).isFalse();
    }

    // ── Valid transitions from IN_PROGRESS ─────────────────────────────

    @Test
    void inProgress_canTransitionToCompleted() {
        assertThat(PickStatus.IN_PROGRESS.canTransitionTo(PickStatus.COMPLETED)).isTrue();
    }

    @Test
    void inProgress_canTransitionToCancelled() {
        assertThat(PickStatus.IN_PROGRESS.canTransitionTo(PickStatus.CANCELLED)).isTrue();
    }

    @Test
    void inProgress_cannotTransitionToPending() {
        assertThat(PickStatus.IN_PROGRESS.canTransitionTo(PickStatus.PENDING)).isFalse();
    }

    @Test
    void inProgress_cannotTransitionToSelf() {
        assertThat(PickStatus.IN_PROGRESS.canTransitionTo(PickStatus.IN_PROGRESS)).isFalse();
    }

    // ── Terminal states ────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(PickStatus.class)
    void completed_cannotTransitionToAnyState(PickStatus target) {
        assertThat(PickStatus.COMPLETED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PickStatus.class)
    void cancelled_cannotTransitionToAnyState(PickStatus target) {
        assertThat(PickStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }

    // ── Invalid transitions ────────────────────────────────────────────

    @Test
    void pending_cannotSkipToCompleted() {
        assertThat(PickStatus.PENDING.canTransitionTo(PickStatus.COMPLETED)).isFalse();
    }

    @Test
    void inProgress_cannotGoBackToPending() {
        assertThat(PickStatus.IN_PROGRESS.canTransitionTo(PickStatus.PENDING)).isFalse();
    }

    @Test
    void completed_isTerminal_noTransitionsToCancelled() {
        assertThat(PickStatus.COMPLETED.canTransitionTo(PickStatus.CANCELLED)).isFalse();
    }

    @Test
    void cancelled_isTerminal_noTransitionsToInProgress() {
        assertThat(PickStatus.CANCELLED.canTransitionTo(PickStatus.IN_PROGRESS)).isFalse();
    }

    // ── Enum values ────────────────────────────────────────────────────

    @Test
    void allValuesPresent() {
        assertThat(PickStatus.values()).containsExactly(
                PickStatus.PENDING,
                PickStatus.IN_PROGRESS,
                PickStatus.COMPLETED,
                PickStatus.CANCELLED
        );
    }

    @Test
    void valueOf_returnsCorrectEnum() {
        assertThat(PickStatus.valueOf("PENDING")).isEqualTo(PickStatus.PENDING);
        assertThat(PickStatus.valueOf("IN_PROGRESS")).isEqualTo(PickStatus.IN_PROGRESS);
        assertThat(PickStatus.valueOf("COMPLETED")).isEqualTo(PickStatus.COMPLETED);
        assertThat(PickStatus.valueOf("CANCELLED")).isEqualTo(PickStatus.CANCELLED);
    }
}
