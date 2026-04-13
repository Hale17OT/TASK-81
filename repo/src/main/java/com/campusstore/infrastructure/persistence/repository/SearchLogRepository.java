package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.SearchLogEntity;
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
public interface SearchLogRepository extends JpaRepository<SearchLogEntity, Long> {

    Page<SearchLogEntity> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);

    @Query(value = "SELECT query_text, COUNT(*) AS cnt FROM search_log WHERE searched_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY query_text ORDER BY cnt DESC LIMIT :limit",
           nativeQuery = true)
    List<Object[]> findTrendingTerms(@Param("limit") int limit);

    @Modifying
    @Transactional
    void deleteBySearchedAtBefore(Instant cutoff);
}
