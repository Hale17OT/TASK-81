package com.campusstore.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddContactRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 60)
    private String label;

    @Size(max = 60)
    private String relationship;

    @Size(max = 200)
    private String name;

    @Email(message = "Email must be a valid email address")
    @Size(max = 200)
    private String email;

    @Size(max = 50)
    private String phone;

    @Size(max = 1000)
    private String notes;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
