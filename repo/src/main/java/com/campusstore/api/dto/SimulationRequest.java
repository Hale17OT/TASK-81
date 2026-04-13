package com.campusstore.api.dto;

import jakarta.validation.constraints.Positive;

/**
 * Shadow-engine simulation request. The primary UI contract is the proposed
 * level-cost multiplier; scenario name / duration remain optional metadata
 * so the admin strategy page can run ad-hoc simulations without ceremony.
 */
public class SimulationRequest {

    private String scenarioName;

    private Integer durationDays;

    /**
     * Proposed level-distance multiplier used by the shadow cost engine.
     * Accepts the same value under the legacy alias "demandMultiplier" to keep
     * any older clients working. Matches {@code verticalWeight} in the full matrix.
     */
    @Positive(message = "proposedLevelMultiplier must be positive")
    private Double proposedLevelMultiplier;

    /** Horizontal (planar) distance weight for the proposed matrix. Optional; defaults to active config. */
    @Positive(message = "horizontalWeight must be positive")
    private Double horizontalWeight;

    /** Zone-transition penalty for the proposed matrix. Optional; 0 means no penalty. */
    private Double zoneTransitionWeight;

    public SimulationRequest() {
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public Double getProposedLevelMultiplier() {
        return proposedLevelMultiplier;
    }

    public void setProposedLevelMultiplier(Double proposedLevelMultiplier) {
        this.proposedLevelMultiplier = proposedLevelMultiplier;
    }

    public Double getHorizontalWeight() {
        return horizontalWeight;
    }

    public void setHorizontalWeight(Double horizontalWeight) {
        this.horizontalWeight = horizontalWeight;
    }

    public Double getZoneTransitionWeight() {
        return zoneTransitionWeight;
    }

    public void setZoneTransitionWeight(Double zoneTransitionWeight) {
        this.zoneTransitionWeight = zoneTransitionWeight;
    }

    // Legacy alias: older clients may post `demandMultiplier`.
    public Double getDemandMultiplier() {
        return proposedLevelMultiplier;
    }

    public void setDemandMultiplier(Double demandMultiplier) {
        if (this.proposedLevelMultiplier == null) {
            this.proposedLevelMultiplier = demandMultiplier;
        }
    }
}
