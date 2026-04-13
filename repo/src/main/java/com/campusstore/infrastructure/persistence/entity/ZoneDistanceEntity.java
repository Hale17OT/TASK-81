package com.campusstore.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "zone_distance")
public class ZoneDistanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_zone_id", insertable = false, updatable = false)
    private Long fromZoneId;

    @Column(name = "to_zone_id", insertable = false, updatable = false)
    private Long toZoneId;

    @Column(name = "weight")
    private BigDecimal weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_zone_id")
    private ZoneEntity fromZone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_zone_id")
    private ZoneEntity toZone;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFromZoneId() {
        return fromZoneId;
    }

    public void setFromZoneId(Long fromZoneId) {
        this.fromZoneId = fromZoneId;
    }

    public Long getToZoneId() {
        return toZoneId;
    }

    public void setToZoneId(Long toZoneId) {
        this.toZoneId = toZoneId;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public ZoneEntity getFromZone() {
        return fromZone;
    }

    public void setFromZone(ZoneEntity fromZone) {
        this.fromZone = fromZone;
    }

    public ZoneEntity getToZone() {
        return toZone;
    }

    public void setToZone(ZoneEntity toZone) {
        this.toZone = toZone;
    }
}
