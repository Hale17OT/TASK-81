package com.campusstore.unit.service;

import com.campusstore.core.domain.model.PickStatus;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.WarehouseService;
import com.campusstore.infrastructure.persistence.entity.PathCostConfigEntity;
import com.campusstore.infrastructure.persistence.entity.PickTaskEntity;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Regression contract tests for {@link WarehouseService#runSimulation(String)} response schema.
 *
 * The admin warehouse dashboard JS (templates/warehouse/index.html) reads the following keys
 * unconditionally: {@code orderCount}, {@code avgStepsSaved}, {@code totalCostSaved},
 * {@code proposedVsActualRatio}. Both the empty (no completed picks) and populated branches
 * must return the same key set so UI rendering stays stable on fresh deployments.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WarehouseSimulationSchemaTest {

    private static final Set<String> REQUIRED_UI_KEYS = Set.of(
            "orderCount", "avgStepsSaved", "totalCostSaved", "proposedVsActualRatio");

    @Mock private StorageLocationRepository storageLocationRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private PickTaskRepository pickTaskRepository;
    @Mock private PathCostConfigRepository pathCostConfigRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private AuditService auditService;

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(
                storageLocationRepository,
                inventoryItemRepository,
                pickTaskRepository,
                pathCostConfigRepository,
                zoneRepository,
                auditService);
    }

    @Test
    void runSimulation_emptyBranch_returnsAllUiKeys() {
        when(pickTaskRepository.findByStatusIn(anyList())).thenReturn(Collections.emptyList());

        Map<String, Object> report = warehouseService.runSimulation("{\"levelMultiplier\":2.0}");

        for (String key : REQUIRED_UI_KEYS) {
            assertTrue(report.containsKey(key),
                    "empty-branch simulation report missing required UI key: " + key);
        }
        assertEquals(0, ((Number) report.get("orderCount")).intValue());
    }

    @Test
    void runSimulation_populatedBranch_hasSameUiKeysAsEmpty() {
        // Populate one completed pick task whose location has coords so the cost math runs.
        StorageLocationEntity loc = new StorageLocationEntity();
        loc.setId(10L);
        loc.setName("L1");
        loc.setXCoord(BigDecimal.valueOf(1));
        loc.setYCoord(BigDecimal.valueOf(1));
        loc.setLevel(0);

        PickTaskEntity task = new PickTaskEntity();
        task.setId(1L);
        task.setRequestId(100L);
        task.setStatus(PickStatus.COMPLETED);
        task.setLocation(loc);
        task.setPickPathCost(BigDecimal.valueOf(5.0));

        when(pickTaskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));

        PathCostConfigEntity cfg = new PathCostConfigEntity();
        cfg.setLevelMultiplier(BigDecimal.valueOf(2.0));
        when(pathCostConfigRepository.findByIsActiveTrue()).thenReturn(List.of(cfg));

        Map<String, Object> report = warehouseService.runSimulation("{\"levelMultiplier\":1.5}");

        for (String key : REQUIRED_UI_KEYS) {
            assertTrue(report.containsKey(key),
                    "populated-branch simulation report missing required UI key: " + key);
        }
    }

    @Test
    void runSimulation_emptyAndPopulated_haveKeyParity() {
        // Empty branch
        when(pickTaskRepository.findByStatusIn(anyList())).thenReturn(Collections.emptyList());
        Map<String, Object> empty = warehouseService.runSimulation("{\"levelMultiplier\":2.0}");

        // Populated branch
        StorageLocationEntity loc = new StorageLocationEntity();
        loc.setId(10L);
        loc.setName("L1");
        loc.setXCoord(BigDecimal.valueOf(1));
        loc.setYCoord(BigDecimal.valueOf(1));
        loc.setLevel(0);

        PickTaskEntity task = new PickTaskEntity();
        task.setId(1L);
        task.setRequestId(100L);
        task.setStatus(PickStatus.COMPLETED);
        task.setLocation(loc);
        task.setPickPathCost(BigDecimal.valueOf(5.0));

        when(pickTaskRepository.findByStatusIn(anyList())).thenReturn(List.of(task));
        PathCostConfigEntity cfg = new PathCostConfigEntity();
        cfg.setLevelMultiplier(BigDecimal.valueOf(2.0));
        when(pathCostConfigRepository.findByIsActiveTrue()).thenReturn(List.of(cfg));

        Map<String, Object> populated = warehouseService.runSimulation("{\"levelMultiplier\":1.5}");

        // Key parity: same key set in both branches.
        assertEquals(empty.keySet(), populated.keySet(),
                "empty and populated simulation reports must expose identical keys");
    }
}
