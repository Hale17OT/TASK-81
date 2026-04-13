package com.campusstore.infrastructure.persistence.repository;

import com.campusstore.infrastructure.persistence.entity.ZoneDistanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneDistanceRepository extends JpaRepository<ZoneDistanceEntity, Long> {

    List<ZoneDistanceEntity> findByFromZoneId(Long fromZoneId);

    Optional<ZoneDistanceEntity> findByFromZoneIdAndToZoneId(Long fromZoneId, Long toZoneId);

    @Query("SELECT zd.weight FROM ZoneDistanceEntity zd WHERE zd.fromZoneId = :fromZoneId AND zd.toZoneId = :toZoneId")
    Optional<BigDecimal> findDistanceWeight(@Param("fromZoneId") Long fromZoneId, @Param("toZoneId") Long toZoneId);
}
