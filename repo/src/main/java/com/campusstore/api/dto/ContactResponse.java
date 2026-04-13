package com.campusstore.api.dto;

/**
 * Owner-scoped contact response. Name/email/phone/notes are decrypted
 * server-side for the authenticated owner only.
 */
public class ContactResponse {

    private Long id;
    private String label;
    private String relationship;
    private String name;
    private String email;
    private String phone;
    private String phoneLast4;
    private String notes;
    private Boolean isPrimary;

    public ContactResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getPhoneLast4() { return phoneLast4; }
    public void setPhoneLast4(String phoneLast4) { this.phoneLast4 = phoneLast4; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
}
