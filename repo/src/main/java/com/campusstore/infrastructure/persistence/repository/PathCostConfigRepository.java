package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.PathCostConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PathCostConfigRepository extends JpaRepository<PathCostConfigEntity, Long> {

    List<PathCostConfigEntity> findByIsActiveTrue();
}
