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

/**
 * Encrypted-at-rest contact record owned by a single {@link UserEntity}. Covers
 * emergency / guardian / office / other labeled contacts the prompt requires next to
 * US-style addresses.
 */
@Entity
@Table(name = "user_contact")
public class UserContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "relationship")
    private String relationship;

    @JsonIgnore
    @Lob
    @Column(name = "name_encrypted")
    private byte[] nameEncrypted;

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

    @JsonIgnore
    @Lob
    @Column(name = "notes_encrypted")
    private byte[] notesEncrypted;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public byte[] getNameEncrypted() { return nameEncrypted; }
    public void setNameEncrypted(byte[] nameEncrypted) { this.nameEncrypted = nameEncrypted; }
    public byte[] getEmailEncrypted() { return emailEncrypted; }
    public void setEmailEncrypted(byte[] emailEncrypted) { this.emailEncrypted = emailEncrypted; }
    public byte[] getPhoneEncrypted() { return phoneEncrypted; }
    public void setPhoneEncrypted(byte[] phoneEncrypted) { this.phoneEncrypted = phoneEncrypted; }
    public String getPhoneLast4() { return phoneLast4; }
    public void setPhoneLast4(String phoneLast4) { this.phoneLast4 = phoneLast4; }
    public byte[] getNotesEncrypted() { return notesEncrypted; }
    public void setNotesEncrypted(byte[] notesEncrypted) { this.notesEncrypted = notesEncrypted; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean primary) { isPrimary = primary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
