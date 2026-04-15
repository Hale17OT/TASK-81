package com.campusstore.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "user_preference")
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "dnd_start_time")
    private LocalTime dndStartTime;

    @Column(name = "dnd_end_time")
    private LocalTime dndEndTime;

    @Column(name = "personalization_enabled")
    private Boolean personalizationEnabled;

    @OneToOne(fetch = FetchType.LAZY)
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

    public LocalTime getDndStartTime() {
        return dndStartTime;
    }

    public void setDndStartTime(LocalTime dndStartTime) {
        this.dndStartTime = dndStartTime;
    }

    public LocalTime getDndEndTime() {
        return dndEndTime;
    }

    public void setDndEndTime(LocalTime dndEndTime) {
        this.dndEndTime = dndEndTime;
    }

    public Boolean getPersonalizationEnabled() {
        return personalizationEnabled;
    }

    public void setPersonalizationEnabled(Boolean personalizationEnabled) {
        this.personalizationEnabled = personalizationEnabled;
    }

    @JsonIgnore
    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }
}
