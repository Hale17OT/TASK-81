package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.BrowsingHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface BrowsingHistoryRepository extends JpaRepository<BrowsingHistoryEntity, Long> {

    Page<BrowsingHistoryEntity> findByUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);

    @Query(value = "SELECT DISTINCT i.category_id FROM browsing_history bh JOIN inventory_item i ON bh.item_id = i.id WHERE bh.user_id = :userId ORDER BY bh.viewed_at DESC LIMIT 30", nativeQuery = true)
    List<Long> findRecentCategoryIdsByUserId(@Param("userId") Long userId);

    /**
     * Ordered list of the last 30 browsed item IDs (most recent first). Duplicates are
     * preserved so repeat interactions carry weight in the personalization score.
     */
    @Query(value = "SELECT bh.item_id FROM browsing_history bh WHERE bh.user_id = :userId ORDER BY bh.viewed_at DESC LIMIT 30", nativeQuery = true)
    List<Long> findRecentItemIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    void deleteByViewedAtBefore(Instant cutoff);

    long countByUserId(Long userId);
}
