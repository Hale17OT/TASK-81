package com.campusstore.core.service;

import com.campusstore.core.domain.event.ConflictException;
import com.campusstore.core.domain.event.ResourceNotFoundException;
import com.campusstore.infrastructure.persistence.entity.ZoneDistanceEntity;
import com.campusstore.infrastructure.persistence.entity.ZoneEntity;
import com.campusstore.infrastructure.persistence.repository.ZoneDistanceRepository;
import com.campusstore.infrastructure.persistence.repository.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ZoneService {

    private static final Logger log = LoggerFactory.getLogger(ZoneService.class);

    private final ZoneRepository zoneRepository;
    private final ZoneDistanceRepository zoneDistanceRepository;
    private final AuditService auditService;

    public ZoneService(ZoneRepository zoneRepository, ZoneDistanceRepository zoneDistanceRepository,
                       AuditService auditService) {
        this.zoneRepository = zoneRepository;
        this.zoneDistanceRepository = zoneDistanceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ZoneEntity> listAll() {
        return zoneRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ZoneEntity getById(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", id));
    }

    @Transactional
    public ZoneEntity create(String name, String description, String building, Integer floorLevel,
                              Long actorUserId) {
        if (zoneRepository.findByName(name).isPresent()) {
            throw new ConflictException("Zone with name '" + name + "' already exists");
        }

        ZoneEntity zone = new ZoneEntity();
        zone.setName(name);
        zone.setDescription(description);
        zone.setBuilding(building);
        zone.setFloorLevel(floorLevel);

        ZoneEntity saved = zoneRepository.save(zone);
        log.info("Zone created: {} (id={})", name, saved.getId());
        auditService.log(actorUserId, "ZONE.CREATE", "Zone", saved.getId(),
                "{\"name\":\"" + name + "\"}");

        return saved;
    }

    @Transactional
    public void setDistance(Long fromZoneId, Long toZoneId, BigDecimal weight,
                            Long actorUserId) {
        // Resolve both endpoints — needed both for validation and for the relationship-based
        // FK write below. The scalar from_zone_id/to_zone_id columns on ZoneDistanceEntity
        // are insertable/updatable=false, so writing them requires setFromZone/setToZone.
        ZoneEntity from = zoneRepository.findById(fromZoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", fromZoneId));
        ZoneEntity to = zoneRepository.findById(toZoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", toZoneId));

        // Upsert forward direction
        ZoneDistanceEntity forward = zoneDistanceRepository
                .findByFromZoneIdAndToZoneId(fromZoneId, toZoneId)
                .orElse(new ZoneDistanceEntity());
        forward.setFromZone(from);
        forward.setToZone(to);
        forward.setWeight(weight);
        zoneDistanceRepository.save(forward);

        // Upsert reverse direction (bidirectional)
        ZoneDistanceEntity reverse = zoneDistanceRepository
                .findByFromZoneIdAndToZoneId(toZoneId, fromZoneId)
                .orElse(new ZoneDistanceEntity());
        reverse.setFromZone(to);
        reverse.setToZone(from);
        reverse.setWeight(weight);
        zoneDistanceRepository.save(reverse);

        log.info("Zone distance set: {} <-> {} = {}", fromZoneId, toZoneId, weight);
        auditService.log(actorUserId, "ZONE_DISTANCE.SET", "ZoneDistance", null,
                "{\"from\":" + fromZoneId + ",\"to\":" + toZoneId + ",\"weight\":" + weight + "}");
    }
}
