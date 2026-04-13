package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<FavoriteEntity, Long> {

    List<FavoriteEntity> findByUserId(Long userId);

    Optional<FavoriteEntity> findByUserIdAndItemId(Long userId, Long itemId);

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    @Modifying
    @Transactional
    void deleteByUserIdAndItemId(Long userId, Long itemId);
}
