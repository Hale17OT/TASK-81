package com.campusstore.infrastructure.persistence.entity;

import com.campusstore.core.domain.model.AccountStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "`user`")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @JsonIgnore
    @Lob
    @Column(name = "password_hash_encrypted")
    private byte[] passwordHashEncrypted;

    @Column(name = "display_name")
    private String displayName;

    @JsonIgnore
    @Lob
    @Column(name = "email_encrypted")
    private byte[] emailEncrypted;

    @JsonIgnore
    @Lob
    @Column(name = "phone_encrypted")
    private byte[] phoneEncrypted;

    @Column(name = "phone_last4")
    private String phoneLast4;

    @Column(name = "home_zone_id", insertable = false, updatable = false)
    private Long homeZoneId;

    @Column(name = "department_id", insertable = false, updatable = false)
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status")
    private AccountStatus accountStatus;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "personalization_enabled")
    private Boolean personalizationEnabled;

    /**
     * Seed / reset accounts start with this flag set — the user is forced to change
     * their password on next login before any other page renders. Cleared after a
     * successful self-service password change.
     */
    @Column(name = "password_change_required")
    private Boolean passwordChangeRequired;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "department_id")
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "home_zone_id")
    private ZoneEntity homeZone;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserRoleEntity> roles = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getPasswordHashEncrypted() {
        return passwordHashEncrypted;
    }

    public void setPasswordHashEncrypted(byte[] passwordHashEncrypted) {
        this.passwordHashEncrypted = passwordHashEncrypted;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public byte[] getEmailEncrypted() {
        return emailEncrypted;
    }

    public void setEmailEncrypted(byte[] emailEncrypted) {
        this.emailEncrypted = emailEncrypted;
    }

    public byte[] getPhoneEncrypted() {
        return phoneEncrypted;
    }

    public void setPhoneEncrypted(byte[] phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    public String getPhoneLast4() {
        return phoneLast4;
    }

    public void setPhoneLast4(String phoneLast4) {
        this.phoneLast4 = phoneLast4;
    }

    public Long getHomeZoneId() {
        return homeZoneId;
    }

    public void setHomeZoneId(Long homeZoneId) {
        this.homeZoneId = homeZoneId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(Instant disabledAt) {
        this.disabledAt = disabledAt;
    }

    public Boolean getPersonalizationEnabled() {
        return personalizationEnabled;
    }

    public void setPersonalizationEnabled(Boolean personalizationEnabled) {
        this.personalizationEnabled = personalizationEnabled;
    }

    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public DepartmentEntity getDepartment() {
        return department;
    }

    public void setDepartment(DepartmentEntity department) {
        this.department = department;
    }

    public ZoneEntity getHomeZone() {
        return homeZone;
    }

    public void setHomeZone(ZoneEntity homeZone) {
        this.homeZone = homeZone;
    }

    public List<UserRoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(List<UserRoleEntity> roles) {
        this.roles = roles;
    }
}
