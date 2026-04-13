package com.campusstore.api.dto;

import com.campusstore.core.domain.model.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ChangeRolesRequest {

    @NotNull(message = "Roles list is required")
    @Size(min = 1, message = "At least one role is required")
    private List<Role> roles;

    public ChangeRolesRequest() {
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }
}
