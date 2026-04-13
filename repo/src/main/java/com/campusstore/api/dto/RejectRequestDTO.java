package com.campusstore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectRequestDTO {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    public RejectRequestDTO() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
