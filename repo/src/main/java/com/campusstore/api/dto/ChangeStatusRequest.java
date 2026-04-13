package com.campusstore.api.dto;

import com.campusstore.core.domain.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

public class ChangeStatusRequest {

    @NotNull(message = "Status is required")
    private AccountStatus status;

    public ChangeStatusRequest() {
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
