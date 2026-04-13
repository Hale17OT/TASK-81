package com.campusstore.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SetZoneDistanceRequest {

    @NotNull(message = "From zone ID is required")
    private Long fromZoneId;

    @NotNull(message = "To zone ID is required")
    private Long toZoneId;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.0", message = "Weight must be zero or positive")
    private BigDecimal weight;

    public SetZoneDistanceRequest() {
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
}
