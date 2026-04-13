package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<ZoneEntity, Long> {

    Optional<ZoneEntity> findByName(String name);
}
