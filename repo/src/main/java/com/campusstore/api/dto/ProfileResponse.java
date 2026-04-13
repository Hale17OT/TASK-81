package com.campusstore.api.dto;

/**
 * Owner-scoped profile response. Returned from {@code GET /api/profile} to the
 * authenticated user only — PII (email, phone) is decrypted server-side for the
 * owner. Transmitted exclusively over TLS (including loopback TLS for the
 * Thymeleaf web layer).
 */
public class ProfileResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String phoneLast4;
    private Long homeZoneId;
    private Long departmentId;
    private Boolean passwordChangeRequired;

    public ProfileResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPhoneLast4() { return phoneLast4; }
    public void setPhoneLast4(String phoneLast4) { this.phoneLast4 = phoneLast4; }
    public Long getHomeZoneId() { return homeZoneId; }
    public void setHomeZoneId(Long homeZoneId) { this.homeZoneId = homeZoneId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Boolean getPasswordChangeRequired() { return passwordChangeRequired; }
    public void setPasswordChangeRequired(Boolean passwordChangeRequired) { this.passwordChangeRequired = passwordChangeRequired; }
}
