package com.campusstore.api.dto;

import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateLocationRequest {

    @NotNull(message = "Zone ID is required")
    private Long zoneId;

    @NotBlank(message = "Name is required")
    private String name;

    private BigDecimal x;

    private BigDecimal y;

    private Integer level;

    private TemperatureZone temperatureZone;

    private SecurityLevel securityLevel;

    @Min(value = 0, message = "Capacity must be zero or positive")
    private int capacity;

    public CreateLocationRequest() {
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

    public BigDecimal getX() {
        return x;
    }

    public void setX(BigDecimal x) {
        this.x = x;
    }

    public BigDecimal getY() {
        return y;
    }

    public void setY(BigDecimal y) {
        this.y = y;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
