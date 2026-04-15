package com.campusstore.unit.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.ABCClassification;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.core.service.AuditService;
import com.campusstore.core.service.InventoryService;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.repository.CategoryRepository;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.StorageLocationRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryService} covering SKU validation,
 * inactive-item visibility, ABC classification bucketing, and
 * heat-score normalization branches.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private StorageLocationRepository storageLocationRepository;
    @Mock private PickTaskRepository pickTaskRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private InventoryService inventoryService;

    // ── createItem ────────────────────────────────────────────────────

    @Test
    void createItem_duplicateSku_throwsConflictException() {
        when(inventoryItemRepository.existsBySku("DUPE-001")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                inventoryService.createItem("Item", "desc", "DUPE-001",
                        null, null, null,
                        BigDecimal.TEN, 5, false, null, ItemCondition.NEW, null));

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void createItem_unknownCategory_throwsResourceNotFoundException() {
        when(inventoryItemRepository.existsBySku("CAT-SKU")).thenReturn(false);
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                inventoryService.createItem("Item", "desc", "CAT-SKU",
                        99L, null, null,
                        BigDecimal.TEN, 5, false, null, ItemCondition.NEW, null));
    }

    @Test
    void createItem_unknownDepartment_throwsResourceNotFoundException() {
        when(inventoryItemRepository.existsBySku("DEPT-SKU")).thenReturn(false);
        when(departmentRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                inventoryService.createItem("Item", "desc", "DEPT-SKU",
                        null, 99L, null,
                        BigDecimal.TEN, 5, false, null, ItemCondition.NEW, null));
    }

    @Test
    void createItem_nullQuantity_defaultsToZero() {
        when(inventoryItemRepository.existsBySku("NULL-QTY")).thenReturn(false);
        InventoryItemEntity saved = new InventoryItemEntity();
        saved.setId(1L);
        saved.setQuantityTotal(0);
        saved.setQuantityAvailable(0);
        when(inventoryItemRepository.save(any())).thenReturn(saved);

        InventoryItemEntity result = inventoryService.createItem("Item", "desc", "NULL-QTY",
                null, null, null, BigDecimal.TEN, null, false, null, ItemCondition.NEW, null);

        assertEquals(0, result.getQuantityTotal());
        assertEquals(0, result.getQuantityAvailable());
    }

    // ── getItem ───────────────────────────────────────────────────────

    @Test
    void getItem_inactiveItem_throwsResourceNotFoundException() {
        InventoryItemEntity inactive = new InventoryItemEntity();
        inactive.setId(42L);
        inactive.setIsActive(false);
        when(inventoryItemRepository.findById(42L)).thenReturn(Optional.of(inactive));

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.getItem(42L));
    }

    @Test
    void getItem_missingItem_throwsResourceNotFoundException() {
        when(inventoryItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.getItem(999L));
    }

    // ── recalculateAbc ────────────────────────────────────────────────

    @Test
    void recalculateAbc_emptyInventory_completesWithoutError() {
        when(pickTaskRepository.countCompletedPicksByLocationSince(any())).thenReturn(List.of());
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        inventoryService.recalculateAbc();

        verify(inventoryItemRepository).saveAll(List.of());
    }

    @Test
    void recalculateAbc_threeItems_bucketsCorrectlyIntoABC() {
        // 3 items: topA = ceil(3*0.20)=1, topB = ceil(3*0.50)=2
        // sorted by pick count desc: high(loc=1,100) → A, med(loc=2,50) → B, low(loc=3,10) → C
        InventoryItemEntity itemHigh = new InventoryItemEntity();
        itemHigh.setLocationId(1L);
        InventoryItemEntity itemMed = new InventoryItemEntity();
        itemMed.setLocationId(2L);
        InventoryItemEntity itemLow = new InventoryItemEntity();
        itemLow.setLocationId(3L);

        when(pickTaskRepository.countCompletedPicksByLocationSince(any()))
                .thenReturn(Arrays.asList(
                        new Object[]{1L, 100L},
                        new Object[]{2L, 50L},
                        new Object[]{3L, 10L}
                ));
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(itemHigh, itemMed, itemLow)));

        inventoryService.recalculateAbc();

        assertEquals(ABCClassification.A, itemHigh.getAbcClassification());
        assertEquals(ABCClassification.B, itemMed.getAbcClassification());
        assertEquals(ABCClassification.C, itemLow.getAbcClassification());
    }

    @Test
    void recalculateAbc_allItemsSamePicks_allClassifiedA() {
        // Only 1 item: topA = ceil(1*0.20)=1, so index 0 < 1 → A
        InventoryItemEntity onlyItem = new InventoryItemEntity();
        onlyItem.setLocationId(1L);

        when(pickTaskRepository.countCompletedPicksByLocationSince(any()))
                .thenReturn(Collections.singletonList(new Object[]{1L, 5L}));
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(onlyItem)));

        inventoryService.recalculateAbc();

        assertEquals(ABCClassification.A, onlyItem.getAbcClassification());
    }

    @Test
    void recalculateAbc_itemWithNoPickHistory_classifiedC() {
        // Item has no pick history — count defaults to 0, should fall into C bucket
        // With 5 items: topA=ceil(5*0.2)=1, topB=ceil(5*0.5)=3
        InventoryItemEntity[] items = new InventoryItemEntity[5];
        for (int i = 0; i < 5; i++) {
            items[i] = new InventoryItemEntity();
            items[i].setLocationId((long) (i + 1));
        }
        // Only first item has picks; rest have no location match → count=0
        when(pickTaskRepository.countCompletedPicksByLocationSince(any()))
                .thenReturn(Collections.singletonList(new Object[]{1L, 50L}));
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(items[0], items[1], items[2], items[3], items[4])));

        inventoryService.recalculateAbc();

        assertEquals(ABCClassification.A, items[0].getAbcClassification());
        // Items 1-4 all have count=0, so after sort they are indices 1-4
        // topB=3, so index 1,2 → B, index 3,4 → C
        assertEquals(ABCClassification.C, items[3].getAbcClassification());
        assertEquals(ABCClassification.C, items[4].getAbcClassification());
    }

    // ── recalculateHeatScores ─────────────────────────────────────────

    @Test
    void recalculateHeatScores_noPickActivity_allZero() {
        InventoryItemEntity item = new InventoryItemEntity();
        item.setLocationId(1L);

        when(pickTaskRepository.countCompletedPicksByLocationSince(any())).thenReturn(List.of());
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        inventoryService.recalculateHeatScores();

        assertEquals(BigDecimal.ZERO, item.getHeatScore());
    }

    @Test
    void recalculateHeatScores_maxCountItem_getsScore1() {
        InventoryItemEntity itemMax = new InventoryItemEntity();
        itemMax.setLocationId(1L);
        InventoryItemEntity itemHalf = new InventoryItemEntity();
        itemHalf.setLocationId(2L);

        when(pickTaskRepository.countCompletedPicksByLocationSince(any()))
                .thenReturn(Arrays.asList(new Object[]{1L, 200L}, new Object[]{2L, 100L}));
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(itemMax, itemHalf)));

        inventoryService.recalculateHeatScores();

        assertEquals(new BigDecimal("1.0000"), itemMax.getHeatScore());
        assertEquals(new BigDecimal("0.5000"), itemHalf.getHeatScore());
    }

    @Test
    void recalculateHeatScores_itemWithNoLocation_getsZeroScore() {
        InventoryItemEntity noLocation = new InventoryItemEntity();
        noLocation.setLocationId(null); // no location → count=0
        InventoryItemEntity withLocation = new InventoryItemEntity();
        withLocation.setLocationId(1L);

        when(pickTaskRepository.countCompletedPicksByLocationSince(any()))
                .thenReturn(Collections.singletonList(new Object[]{1L, 10L}));
        when(inventoryItemRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withLocation, noLocation)));

        inventoryService.recalculateHeatScores();

        assertEquals(new BigDecimal("1.0000"), withLocation.getHeatScore());
        assertEquals(new BigDecimal("0.0000"), noLocation.getHeatScore());
    }
}
