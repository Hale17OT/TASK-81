package com.campusstore.core.port.inbound;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound port for zone operations.
 */
public interface ZoneUseCase {

    /**
     * List all zones.
     *
     * @return list of zone views
     */
    List<ZoneView> listZones();

    /**
     * Create a new zone.
     *
     * @param name        the zone name
     * @param description the zone description
     * @param building    the building name
     * @param floorLevel  the floor level
     * @return the id of the created zone
     */
    Long createZone(String name, String description, String building, Integer floorLevel);

    /**
     * Set or update the distance weight between two zones.
     *
     * @param fromZoneId the source zone id
     * @param toZoneId   the target zone id
     * @param weight     the distance weight
     */
    void setZoneDistance(Long fromZoneId, Long toZoneId, BigDecimal weight);

    // ── View types ─────────────────────────────────────────────────────

    record ZoneView(
            Long id,
            String name,
            String description,
            String building,
            Integer floorLevel
    ) {
    }
}
