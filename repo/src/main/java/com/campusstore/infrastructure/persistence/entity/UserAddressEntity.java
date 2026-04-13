package com.campusstore.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_address")
public class UserAddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "label")
    private String label;

    @JsonIgnore
    @Lob
    @Column(name = "street_encrypted")
    private byte[] streetEncrypted;

    @JsonIgnore
    @Lob
    @Column(name = "city_encrypted")
    private byte[] cityEncrypted;

    @JsonIgnore
    @Lob
    @Column(name = "state_encrypted")
    private byte[] stateEncrypted;

    @JsonIgnore
    @Lob
    @Column(name = "zip_code_encrypted")
    private byte[] zipCodeEncrypted;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "created_at")
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public byte[] getStreetEncrypted() {
        return streetEncrypted;
    }

    public void setStreetEncrypted(byte[] streetEncrypted) {
        this.streetEncrypted = streetEncrypted;
    }

    public byte[] getCityEncrypted() {
        return cityEncrypted;
    }

    public void setCityEncrypted(byte[] cityEncrypted) {
        this.cityEncrypted = cityEncrypted;
    }

    public byte[] getStateEncrypted() {
        return stateEncrypted;
    }

    public void setStateEncrypted(byte[] stateEncrypted) {
        this.stateEncrypted = stateEncrypted;
    }

    public byte[] getZipCodeEncrypted() {
        return zipCodeEncrypted;
    }

    public void setZipCodeEncrypted(byte[] zipCodeEncrypted) {
        this.zipCodeEncrypted = zipCodeEncrypted;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }
}
