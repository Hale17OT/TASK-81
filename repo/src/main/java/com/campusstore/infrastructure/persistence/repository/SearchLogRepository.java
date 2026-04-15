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

    @Query("SELECT sl.queryText, COUNT(sl) FROM SearchLogEntity sl WHERE sl.searchedAt >= :since GROUP BY sl.queryText ORDER BY COUNT(sl) DESC")
    List<Object[]> findTrendingTerms(@Param("since") Instant since);

    @Modifying
    @Transactional
    void deleteBySearchedAtBefore(Instant cutoff);
}
