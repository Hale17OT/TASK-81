package com.campusstore.unit.service;

import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.WarehouseService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.entity.ItemRequestEntity;
import com.campusstore.infrastructure.persistence.entity.PathCostConfigEntity;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.PathCostConfigRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.StorageLocationRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the configurable path-cost matrix and FIFO/expiration-aware pick
 * ordering in {@link WarehouseService#generatePickPath(java.util.List)}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WarehousePickStrategyTest {

    @Mock private StorageLocationRepository storageLocationRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private PickTaskRepository pickTaskRepository;
    @Mock private PathCostConfigRepository pathCostConfigRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private AuditService auditService;

    private WarehouseService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseService(storageLocationRepository, inventoryItemRepository,
                pickTaskRepository, pathCostConfigRepository, zoneRepository, auditService);
    }

    /**
     * Item A is physically farther but expires tomorrow; item B is close but doesn't
     * expire for months. FIFO/expiration policy must pick A first.
     */
    @Test
    void generatePickPath_urgentExpirationBeatsProximity() {
        ZoneEntity zone = new ZoneEntity();
        zone.setId(1L);

        StorageLocationEntity farLoc = makeLoc(10L, 100, 100, 0, zone, "Far");
        StorageLocationEntity nearLoc = makeLoc(20L, 1, 1, 0, zone, "Near");

        PickTaskEntity urgent = makePickTask(1L, farLoc,
                makeItem(1000L, LocalDate.now().plusDays(1))); // expires in 1 day
        PickTaskEntity normal = makePickTask(2L, nearLoc,
                makeItem(2000L, LocalDate.now().plusYears(1))); // expires in a year

        when(pickTaskRepository.findById(1L)).thenReturn(Optional.of(urgent));
        when(pickTaskRepository.findById(2L)).thenReturn(Optional.of(normal));
        when(pathCostConfigRepository.findByIsActiveTrue()).thenReturn(List.of());

        Map<String, Object> result = service.generatePickPath(List.of(1L, 2L));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertTrue(steps.size() >= 2, "expected two pick stops plus origin return");
        assertEquals(1L, steps.get(0).get("pickTaskId"),
                "urgent-expiration item must be picked first regardless of distance");
        assertEquals(2L, steps.get(1).get("pickTaskId"),
                "non-urgent item picked after urgent bucket is drained");
    }

    /**
     * Regression guard: FIFO tie-break must compare LocalDate by value, not reference.
     * Two different LocalDate instances representing the same day must be treated as
     * equal so the createdAt FIFO rule fires.
     */
    @Test
    void generatePickPath_fifoTieBreakUsesValueEqualityOnDates() {
        ZoneEntity zone = new ZoneEntity();
        zone.setId(1L);
        StorageLocationEntity locA = makeLoc(10L, 5, 0, 0, zone, "A");
        StorageLocationEntity locB = makeLoc(20L, 5, 0, 0, zone, "B");

        // Same calendar date but constructed as independent LocalDate instances so
        // `==` would return false — the service must still treat them as equal.
        LocalDate sameDate1 = LocalDate.of(2026, 9, 1);
        LocalDate sameDate2 = LocalDate.of(2026, 9, 1);
        // Sanity: these are distinct objects with equal values.
        org.junit.jupiter.api.Assertions.assertEquals(sameDate1, sameDate2);

        InventoryItemEntity itemA = makeItem(1000L, sameDate1);
        itemA.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z")); // older
        InventoryItemEntity itemB = makeItem(2000L, sameDate2);
        itemB.setCreatedAt(java.time.Instant.parse("2026-03-01T00:00:00Z"));

        PickTaskEntity a = makePickTask(1L, locA, itemA);
        PickTaskEntity b = makePickTask(2L, locB, itemB);

        when(pickTaskRepository.findById(1L)).thenReturn(Optional.of(a));
        when(pickTaskRepository.findById(2L)).thenReturn(Optional.of(b));
        when(pathCostConfigRepository.findByIsActiveTrue()).thenReturn(List.of());

        // Pass B first in the input list — if value equality fails, the nearest-neighbor
        // would pick B (first encountered at equal cost). With value equality, createdAt
        // FIFO kicks in and A (older) wins.
        Map<String, Object> result = service.generatePickPath(List.of(2L, 1L));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        assertEquals(1L, steps.get(0).get("pickTaskId"),
                "older item must win FIFO tie-break even when expiration is stored as a distinct LocalDate instance");
    }

    /**
     * With two items in the same urgency bucket, FIFO breaks the tie: the item with the
     * earlier {@code item.createdAt} should be picked first when costs are equal.
     */
    @Test
    void generatePickPath_fifoTieBreakerWithinBucket() {
        ZoneEntity zone = new ZoneEntity();
        zone.setId(1L);
        StorageLocationEntity locA = makeLoc(10L, 5, 0, 0, zone, "A");
        StorageLocationEntity locB = makeLoc(20L, 5, 0, 0, zone, "B");

        InventoryItemEntity itemA = makeItem(1000L, null);
        itemA.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z")); // older → FIFO first
        InventoryItemEntity itemB = makeItem(2000L, null);
        itemB.setCreatedAt(java.time.Instant.parse("2026-03-01T00:00:00Z"));

        PickTaskEntity a = makePickTask(1L, locA, itemA);
        PickTaskEntity b = makePickTask(2L, locB, itemB);

        when(pickTaskRepository.findById(1L)).thenReturn(Optional.of(a));
        when(pickTaskRepository.findById(2L)).thenReturn(Optional.of(b));
        when(pathCostConfigRepository.findByIsActiveTrue()).thenReturn(List.of());

        Map<String, Object> result = service.generatePickPath(List.of(2L, 1L));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
        // Both at equal distance; FIFO on itemCreatedAt makes A win.
        assertEquals(1L, steps.get(0).get("pickTaskId"),
                "older item should be picked first under FIFO tie-break");
    }

    /**
     * config_json provides a custom horizontalWeight of 10.0 and zoneTransitionWeight of 50.0.
     * Total cost must be meaningfully higher than the default matrix would produce.
     */
    @Test
    void generatePickPath_usesMatrixFromConfigJson() {
        ZoneEntity zone1 = new ZoneEntity();
        zone1.setId(1L);
        ZoneEntity zone2 = new ZoneEntity();
        zone2.setId(2L);

        StorageLocationEntity l1 = makeLoc(10L, 0, 0, 0, zone1, "Z1");
        StorageLocationEntity l2 = makeLoc(20L, 1, 0, 0, zone2, "Z2");

        PickTaskEntity t1 = makePickTask(1L, l1, makeItem(1L, null));
        PickTaskEntity t2 = makePickTask(2L, l2, makeItem(2L, null));

        when(pickTaskRepository.findById(1L)).thenReturn(Optional.of(t1));
        when(pickTaskRepository.findById(2L)).thenReturn(Optional.of(t2));

        PathCostConfigEntity cfg = new PathCostConfigEntity();
        cfg.setLevelMultiplier(BigDecimal.valueOf(2.0));
        cfg.setConfigJson("{\"horizontalWeight\": 10.0, \"zoneTransitionWeight\": 50.0}");
        when(pathCostConfigRepository.findByIsActiveTrue()).thenReturn(List.of(cfg));

        Map<String, Object> result = service.generatePickPath(List.of(1L, 2L));

        BigDecimal totalCost = (BigDecimal) result.get("totalCost");
        // Default matrix (horizontal=1.0, no zone penalty) would produce ~2.0 round trip.
        // With horizontalWeight=10 and zoneTransitionWeight=50 we expect dramatically higher.
        assertTrue(totalCost.doubleValue() > 50.0,
                "matrix coefficients must be honored; got " + totalCost);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private StorageLocationEntity makeLoc(Long id, double x, double y, int level,
                                           ZoneEntity zone, String name) {
        StorageLocationEntity loc = new StorageLocationEntity();
        loc.setId(id);
        loc.setXCoord(BigDecimal.valueOf(x));
        loc.setYCoord(BigDecimal.valueOf(y));
        loc.setLevel(level);
        loc.setZone(zone);
        loc.setName(name);
        return loc;
    }

    private InventoryItemEntity makeItem(Long id, LocalDate expirationDate) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setId(id);
        item.setExpirationDate(expirationDate);
        return item;
    }

    private PickTaskEntity makePickTask(Long id, StorageLocationEntity loc, InventoryItemEntity item) {
        PickTaskEntity pt = new PickTaskEntity();
        pt.setId(id);
        pt.setLocation(loc);
        ItemRequestEntity req = new ItemRequestEntity();
        req.setItem(item);
        pt.setRequest(req);
        return pt;
    }
}
