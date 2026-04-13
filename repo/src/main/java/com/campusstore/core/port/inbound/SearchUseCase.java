package com.campusstore.core.port.inbound;

import com.campusstore.core.domain.model.ItemCondition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound port for search, browsing history, and favorites operations.
 */
public interface SearchUseCase {

    /**
     * Execute a search query against the inventory.
     *
     * @param query the search query with filters, sorting, and pagination
     * @return a page of search results
     */
    Page<SearchResult> search(SearchQuery query);

    /**
     * Get currently trending search terms.
     *
     * @return list of trending keyword strings
     */
    List<String> getTrendingTerms();

    /**
     * Get a user's past search queries.
     *
     * @param userId   the user id
     * @param pageable pagination information
     * @return a page of search history entries
     */
    Page<SearchHistoryEntry> getSearchHistory(Long userId, Pageable pageable);

    /**
     * Record that a user browsed/viewed an item.
     *
     * @param userId the user id
     * @param itemId the item id
     */
    void recordBrowse(Long userId, Long itemId);

    /**
     * Get a user's favorite items.
     *
     * @param userId the user id
     * @return list of favorited item ids
     */
    List<Long> getFavorites(Long userId);

    /**
     * Add an item to a user's favorites.
     *
     * @param userId the user id
     * @param itemId the item id
     */
    void addFavorite(Long userId, Long itemId);

    /**
     * Remove an item from a user's favorites.
     *
     * @param userId the user id
     * @param itemId the item id
     */
    void removeFavorite(Long userId, Long itemId);

    /**
     * Toggle search-result personalization for a user.
     *
     * @param userId  the user id
     * @param enabled whether personalization is enabled
     */
    void togglePersonalization(Long userId, boolean enabled);

    // ── Query and result types ─────────────────────────────────────────

    record SearchQuery(
            String keyword,
            Long categoryId,
            BigDecimal priceMin,
            BigDecimal priceMax,
            ItemCondition condition,
            Long zoneId,
            String sortBy,
            Long userId,
            boolean personalizationEnabled,
            Pageable pageable
    ) {
    }

    record SearchResult(
            Long itemId,
            String name,
            String description,
            BigDecimal priceUsd,
            ItemCondition condition,
            int quantityAvailable,
            double relevanceScore
    ) {
    }

    record SearchHistoryEntry(
            Long id,
            String queryText,
            int resultCount,
            java.time.Instant searchedAt
    ) {
    }
}
