package com.campusstore.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class PickPathRequest {

    @NotNull(message = "Pick task IDs are required")
    @Size(min = 1, message = "At least one pick task ID is required")
    private List<Long> pickTaskIds;

    public PickPathRequest() {
    }

    public List<Long> getPickTaskIds() {
        return pickTaskIds;
    }

    public void setPickTaskIds(List<Long> pickTaskIds) {
        this.pickTaskIds = pickTaskIds;
    }
}
