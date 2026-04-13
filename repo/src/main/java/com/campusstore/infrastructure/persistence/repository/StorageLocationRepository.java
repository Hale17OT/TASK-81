package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import com.campusstore.infrastructure.persistence.entity.StorageLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocationEntity, Long> {

    List<StorageLocationEntity> findByZoneId(Long zoneId);

    List<StorageLocationEntity> findByTemperatureZone(TemperatureZone temperatureZone);

    List<StorageLocationEntity> findBySecurityLevel(SecurityLevel securityLevel);

    @Query("SELECT sl FROM StorageLocationEntity sl WHERE sl.currentOccupancy < sl.capacity ORDER BY (sl.capacity - sl.currentOccupancy) DESC")
    List<StorageLocationEntity> findLocationsWithAvailableCapacity();
}
