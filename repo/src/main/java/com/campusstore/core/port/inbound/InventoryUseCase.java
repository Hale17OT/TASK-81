package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.ABCClassification;
import com.campusstore.core.domain.model.ItemCondition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound port for inventory management operations.
 */
public interface InventoryUseCase {

    /**
     * List inventory items with pagination and optional filters.
     *
     * @param pageable pagination information
     * @param filters  optional search/filter criteria
     * @return a page of item views
     */
    Page<ItemView> listItems(Pageable pageable, ItemFilterCriteria filters);

    /**
     * Get a single inventory item by id.
     *
     * @param id the item id
     * @return the item view
     */
    ItemView getItem(Long id);

    /**
     * Create a new inventory item.
     *
     * @param command the item creation details
     * @return the id of the created item
     */
    Long createItem(CreateItemCommand command);

    /**
     * Update an existing inventory item.
     *
     * @param id      the item id
     * @param command the fields to update
     */
    void updateItem(Long id, UpdateItemCommand command);

    /**
     * Soft-deactivate an inventory item.
     *
     * @param id the item id
     */
    void deactivateItem(Long id);

    /**
     * Recalculate ABC classification for all items.
     */
    void recalculateAbc();

    /**
     * Recalculate heat scores for all items.
     */
    void recalculateHeatScores();

    // ── Command types ──────────────────────────────────────────────────

    record CreateItemCommand(
            String name,
            String description,
            String sku,
            Long categoryId,
            Long departmentId,
            Long locationId,
            BigDecimal priceUsd,
            ItemCondition itemCondition,
            int quantityTotal,
            int quantityAvailable,
            boolean requiresApproval,
            LocalDate expirationDate
    ) {
    }

    record UpdateItemCommand(
            String name,
            String description,
            String sku,
            Long categoryId,
            Long departmentId,
            Long locationId,
            BigDecimal priceUsd,
            ItemCondition itemCondition,
            Integer quantityTotal,
            Integer quantityAvailable,
            Boolean requiresApproval,
            LocalDate expirationDate
    ) {
    }

    record ItemFilterCriteria(
            String keyword,
            Long categoryId,
            Long departmentId,
            ItemCondition condition,
            ABCClassification abcClassification,
            Boolean active
    ) {
    }

    // ── View types ─────────────────────────────────────────────────────

    record ItemView(
            Long id,
            String name,
            String description,
            String sku,
            Long categoryId,
            Long departmentId,
            Long locationId,
            BigDecimal priceUsd,
            ItemCondition itemCondition,
            int quantityTotal,
            int quantityAvailable,
            boolean requiresApproval,
            ABCClassification abcClassification,
            LocalDate expirationDate,
            int popularityScore,
            BigDecimal heatScore,
            boolean active
    ) {
    }
}
