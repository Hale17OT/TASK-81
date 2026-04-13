package com.campusstore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddTagRequest {

    @NotBlank(message = "Tag is required")
    @Size(max = 50, message = "Tag must not exceed 50 characters")
    private String tag;

    public AddTagRequest() {
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
