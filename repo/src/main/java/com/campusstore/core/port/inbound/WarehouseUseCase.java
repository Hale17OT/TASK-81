package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound port for warehouse / storage-location operations.
 */
public interface WarehouseUseCase {

    /**
     * Recommend a putaway location for an item.
     *
     * @param request the putaway request details
     * @return the putaway result with recommended location
     */
    PutawayResult recommendPutaway(PutawayRequest request);

    /**
     * Generate an optimised pick path for a set of pick tasks.
     *
     * @param pickTaskIds the ids of the pick tasks to route
     * @return the pick path result with ordered stops and cost
     */
    PickPathResult generatePickPath(List<Long> pickTaskIds);

    /**
     * Run a warehouse simulation (e.g. layout optimisation).
     *
     * @param request the simulation parameters
     * @return the simulation result
     */
    SimulationResult runSimulation(SimulationRequest request);

    /**
     * List storage locations with pagination.
     *
     * @param pageable pagination information
     * @return a page of location views
     */
    Page<LocationView> listLocations(Pageable pageable);

    /**
     * Create a new storage location.
     *
     * @param command the location details
     * @return the id of the created location
     */
    Long createLocation(CreateLocationCommand command);

    /**
     * Update an existing storage location.
     *
     * @param id      the location id
     * @param command the updated location details
     */
    void updateLocation(Long id, CreateLocationCommand command);

    // ── Command types ──────────────────────────────────────────────────

    record PutawayRequest(
            Long itemId,
            int quantity,
            TemperatureZone requiredTempZone,
            SecurityLevel requiredSecurityLevel
    ) {
    }

    record SimulationRequest(
            String scenarioName,
            int durationDays,
            double demandMultiplier
    ) {
    }

    record CreateLocationCommand(
            Long zoneId,
            String name,
            BigDecimal xCoord,
            BigDecimal yCoord,
            Integer level,
            TemperatureZone temperatureZone,
            SecurityLevel securityLevel,
            int capacity
    ) {
    }

    // ── Result types ───────────────────────────────────────────────────

    record PutawayResult(
            Long recommendedLocationId,
            String locationName,
            BigDecimal travelCost,
            String reason
    ) {
    }

    record PickPathResult(
            List<PickStop> stops,
            BigDecimal totalCost
    ) {
    }

    record PickStop(
            Long pickTaskId,
            Long locationId,
            String locationName,
            int sequenceOrder
    ) {
    }

    record SimulationResult(
            String scenarioName,
            double throughput,
            double averagePickTime,
            double spaceUtilization,
            String summary
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record LocationView(
            Long id,
            Long zoneId,
            String name,
            BigDecimal xCoord,
            BigDecimal yCoord,
            Integer level,
            TemperatureZone temperatureZone,
            SecurityLevel securityLevel,
            int capacity,
            int currentOccupancy
    ) {
    }
}
