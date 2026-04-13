package com.campusstore.api.dto;

import com.campusstore.core.domain.model.SecurityLevel;
import com.campusstore.core.domain.model.TemperatureZone;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PutawayRequest {

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;

    private TemperatureZone requiredTempZone;

    private SecurityLevel requiredSecurityLevel;

    public PutawayRequest() {
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public TemperatureZone getRequiredTempZone() {
        return requiredTempZone;
    }

    public void setRequiredTempZone(TemperatureZone requiredTempZone) {
        this.requiredTempZone = requiredTempZone;
    }

    public SecurityLevel getRequiredSecurityLevel() {
        return requiredSecurityLevel;
    }

    public void setRequiredSecurityLevel(SecurityLevel requiredSecurityLevel) {
        this.requiredSecurityLevel = requiredSecurityLevel;
    }
}
