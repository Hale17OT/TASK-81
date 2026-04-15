package com.campusstore.infrastructure.persistence.entity;

import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "storage_location")
public class StorageLocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", insertable = false, updatable = false)
    private Long zoneId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "x_coord")
    private BigDecimal xCoord;

    @Column(name = "y_coord")
    private BigDecimal yCoord;

    @Column(name = "level")
    private Integer level;

    @Enumerated(EnumType.STRING)
    @Column(name = "temperature_zone")
    private TemperatureZone temperatureZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_level")
    private SecurityLevel securityLevel;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "current_occupancy")
    private Integer currentOccupancy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private ZoneEntity zone;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getXCoord() {
        return xCoord;
    }

    public void setXCoord(BigDecimal xCoord) {
        this.xCoord = xCoord;
    }

    public BigDecimal getYCoord() {
        return yCoord;
    }

    public void setYCoord(BigDecimal yCoord) {
        this.yCoord = yCoord;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public TemperatureZone getTemperatureZone() {
        return temperatureZone;
    }

    public void setTemperatureZone(TemperatureZone temperatureZone) {
        this.temperatureZone = temperatureZone;
    }

    public SecurityLevel getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(SecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getCurrentOccupancy() {
        return currentOccupancy;
    }

    public void setCurrentOccupancy(Integer currentOccupancy) {
        this.currentOccupancy = currentOccupancy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonIgnore
    public ZoneEntity getZone() {
        return zone;
    }

    public void setZone(ZoneEntity zone) {
        this.zone = zone;
    }
}
