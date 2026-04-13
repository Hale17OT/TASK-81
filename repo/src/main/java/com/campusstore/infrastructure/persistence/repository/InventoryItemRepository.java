package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.InventoryItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, Long> {

    Page<InventoryItemEntity> findByIsActiveTrue(Pageable pageable);

    Page<InventoryItemEntity> findByCategoryId(Long categoryId, Pageable pageable);

    Page<InventoryItemEntity> findByDepartmentId(Long departmentId, Pageable pageable);

    Optional<InventoryItemEntity> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query(value = "SELECT * FROM inventory_item WHERE MATCH(name, description) AGAINST(:searchTerm IN BOOLEAN MODE) AND is_active = true",
           countQuery = "SELECT COUNT(*) FROM inventory_item WHERE MATCH(name, description) AGAINST(:searchTerm IN BOOLEAN MODE) AND is_active = true",
           nativeQuery = true)
    Page<InventoryItemEntity> searchByFullText(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT i FROM InventoryItemEntity i JOIN i.location sl WHERE sl.zoneId = :zoneId")
    List<InventoryItemEntity> findByZoneId(@Param("zoneId") Long zoneId);

    List<InventoryItemEntity> findByExpirationDateBeforeAndIsActiveTrue(LocalDate date);
}
