package com.campusstore.core.port.inbound;

import java.util.List;

/**
 * Inbound port for category operations.
 */
public interface CategoryUseCase {

    /**
     * List all categories.
     *
     * @return list of category views
     */
    List<CategoryView> listCategories();

    /**
     * Create a new category.
     *
     * @param name        the category name
     * @param description the category description
     * @param parentId    the parent category id (nullable)
     * @return the id of the created category
     */
    Long createCategory(String name, String description, Long parentId);

    // ── View types ─────────────────────────────────────────────────────

    record CategoryView(
            Long id,
            String name,
            String description,
            Long parentId
    ) {
    }
}
