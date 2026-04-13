package com.campusstore.core.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.core.domain.model.ABCClassification;
import com.campusstore.core.domain.model.ItemCondition;
import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import com.campusstore.infrastructure.persistence.repository.CategoryRepository;
import com.campusstore.infrastructure.persistence.repository.DepartmentRepository;
import com.campusstore.infrastructure.persistence.repository.InventoryItemRepository;
import com.campusstore.infrastructure.persistence.repository.PickTaskRepository;
import com.campusstore.infrastructure.persistence.repository.StorageLocationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final PickTaskRepository pickTaskRepository;
    private final AuditService auditService;

    public InventoryService(InventoryItemRepository inventoryItemRepository,
                            CategoryRepository categoryRepository,
                            DepartmentRepository departmentRepository,
                            StorageLocationRepository storageLocationRepository,
                            PickTaskRepository pickTaskRepository,
                            AuditService auditService) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.pickTaskRepository = pickTaskRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemEntity> listItems(Pageable pageable) {
        return inventoryItemRepository.findByIsActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public InventoryItemEntity getItem(Long itemId) {
        Objects.requireNonNull(itemId, "Item ID must not be null");
        InventoryItemEntity item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemId));
        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new ResourceNotFoundException("InventoryItem", itemId);
        }
        return item;
    }

    public InventoryItemEntity createItem(String name, String description, String sku,
                                           Long categoryId, Long departmentId, Long locationId,
                                           BigDecimal priceUsd, Integer quantityTotal,
                                           Boolean requiresApproval, Long createdByUserId,
                                           ItemCondition condition, LocalDate expirationDate) {
        Objects.requireNonNull(name, "Item name must not be null");
        Objects.requireNonNull(sku, "SKU must not be null");

        if (inventoryItemRepository.existsBySku(sku)) {
            throw new ConflictException("SKU already exists: " + sku);
        }

        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId);
        }
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department", departmentId);
        }
        if (locationId != null && !storageLocationRepository.existsById(locationId)) {
            throw new ResourceNotFoundException("StorageLocation", locationId);
        }

        InventoryItemEntity item = new InventoryItemEntity();
        item.setName(name);
        item.setDescription(description);
        item.setSku(sku);
        if (categoryId != null) {
            item.setCategory(categoryRepository.findById(categoryId).orElse(null));
        }
        if (departmentId != null) {
            item.setDepartment(departmentRepository.findById(departmentId).orElse(null));
        }
        if (locationId != null) {
            item.setLocation(storageLocationRepository.findById(locationId).orElse(null));
        }
        item.setPriceUsd(priceUsd);
        item.setItemCondition(condition);
        item.setQuantityTotal(quantityTotal != null ? quantityTotal : 0);
        item.setQuantityAvailable(quantityTotal != null ? quantityTotal : 0);
        item.setRequiresApproval(requiresApproval != null ? requiresApproval : true);
        item.setAbcClassification(ABCClassification.C);
        item.setExpirationDate(expirationDate);
        item.setIsActive(true);
        item.setHeatScore(BigDecimal.ZERO);
        item.setPopularityScore(0);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());

        InventoryItemEntity saved = inventoryItemRepository.save(item);

        auditService.log(createdByUserId, "CREATE_ITEM", "InventoryItem", saved.getId(),
                "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\"}");
        log.info("Inventory item created with id={}, sku={}", saved.getId(), sku);
        return saved;
    }

    public InventoryItemEntity updateItem(Long itemId, String name, String description,
                                           BigDecimal priceUsd, Integer quantityTotal,
                                           Boolean requiresApproval, Long updatedByUserId) {
        Objects.requireNonNull(itemId, "Item ID must not be null");

        InventoryItemEntity item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemId));

        if (name != null) {
            item.setName(name);
        }
        if (description != null) {
            item.setDescription(description);
        }
        if (priceUsd != null) {
            item.setPriceUsd(priceUsd);
        }
        if (quantityTotal != null) {
            int diff = quantityTotal - (item.getQuantityTotal() != null ? item.getQuantityTotal() : 0);
            item.setQuantityTotal(quantityTotal);
            int currentAvailable = item.getQuantityAvailable() != null ? item.getQuantityAvailable() : 0;
            item.setQuantityAvailable(Math.max(0, currentAvailable + diff));
        }
        if (requiresApproval != null) {
            item.setRequiresApproval(requiresApproval);
        }
        item.setUpdatedAt(Instant.now());

        InventoryItemEntity saved = inventoryItemRepository.save(item);

        auditService.log(updatedByUserId, "UPDATE_ITEM", "InventoryItem", itemId,
                "{\"updatedFields\":\"item\"}");
        log.info("Inventory item updated id={}", itemId);
        return saved;
    }

    public void deactivateItem(Long itemId, Long deactivatedByUserId) {
        Objects.requireNonNull(itemId, "Item ID must not be null");

        InventoryItemEntity item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", itemId));

        item.setIsActive(false);
        item.setUpdatedAt(Instant.now());
        inventoryItemRepository.save(item);

        auditService.log(deactivatedByUserId, "DEACTIVATE_ITEM", "InventoryItem", itemId, "{}");
        log.info("Inventory item deactivated id={}", itemId);
    }

    public void recalculateAbc() {
        log.info("Starting ABC classification recalculation");
        Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);

        // Query pick frequency per item location from last 90 days
        List<Object[]> pickCounts = pickTaskRepository.countCompletedPicksByLocationSince(ninetyDaysAgo);

        // Map locationId -> pick count
        Map<Long, Long> locationPickMap = pickCounts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        // Get all active items and map their pick frequency
        List<InventoryItemEntity> allItems = inventoryItemRepository.findByIsActiveTrue(Pageable.unpaged()).getContent();

        // Create list of item with pick counts
        List<ItemPickCount> itemPicks = new ArrayList<>();
        for (InventoryItemEntity item : allItems) {
            Long locationId = item.getLocationId();
            long count = locationId != null && locationPickMap.containsKey(locationId)
                    ? locationPickMap.get(locationId) : 0L;
            itemPicks.add(new ItemPickCount(item, count));
        }

        // Sort by pick count descending
        itemPicks.sort(Comparator.comparingLong(ItemPickCount::count).reversed());

        int totalItems = itemPicks.size();
        int topA = (int) Math.ceil(totalItems * 0.20);
        int topB = (int) Math.ceil(totalItems * 0.50); // top 20% + next 30% = top 50%

        for (int i = 0; i < totalItems; i++) {
            InventoryItemEntity item = itemPicks.get(i).item();
            ABCClassification classification;
            if (i < topA) {
                classification = ABCClassification.A;
            } else if (i < topB) {
                classification = ABCClassification.B;
            } else {
                classification = ABCClassification.C;
            }
            item.setAbcClassification(classification);
            item.setUpdatedAt(Instant.now());
        }

        inventoryItemRepository.saveAll(allItems);
        log.info("ABC classification recalculation completed for {} items", totalItems);
    }

    public void recalculateHeatScores() {
        log.info("Starting heat score recalculation");
        Instant fourteenDaysAgo = Instant.now().minus(14, ChronoUnit.DAYS);

        // Query pick counts from last 14 days per location
        List<Object[]> pickCounts = pickTaskRepository.countCompletedPicksByLocationSince(fourteenDaysAgo);
        Map<Long, Long> locationPickMap = pickCounts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        List<InventoryItemEntity> allItems = inventoryItemRepository.findByIsActiveTrue(Pageable.unpaged()).getContent();

        // Find max pick count for normalization
        long maxCount = 0;
        for (InventoryItemEntity item : allItems) {
            Long locationId = item.getLocationId();
            long count = locationId != null && locationPickMap.containsKey(locationId)
                    ? locationPickMap.get(locationId) : 0L;
            if (count > maxCount) {
                maxCount = count;
            }
        }

        // Normalize to 0-1 scale
        for (InventoryItemEntity item : allItems) {
            Long locationId = item.getLocationId();
            long count = locationId != null && locationPickMap.containsKey(locationId)
                    ? locationPickMap.get(locationId) : 0L;

            BigDecimal heatScore;
            if (maxCount == 0) {
                heatScore = BigDecimal.ZERO;
            } else {
                heatScore = BigDecimal.valueOf(count)
                        .divide(BigDecimal.valueOf(maxCount), 4, RoundingMode.HALF_UP);
            }
            item.setHeatScore(heatScore);
            item.setUpdatedAt(Instant.now());
        }

        inventoryItemRepository.saveAll(allItems);
        log.info("Heat score recalculation completed for {} items", allItems.size());
    }

    private record ItemPickCount(InventoryItemEntity item, long count) {}
}
