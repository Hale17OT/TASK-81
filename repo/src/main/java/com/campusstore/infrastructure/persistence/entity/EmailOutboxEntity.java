package com.campusstore.infrastructure.persistence.entity;

import com.campusstore.core.domain.model.EmailOutboxStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @JsonIgnore
    @Lob
    @Column(name = "recipient_address_encrypted")
    private byte[] recipientAddressEncrypted;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @JsonIgnore
    @Column(name = "eml_data", columnDefinition = "LONGTEXT")
    private String emlData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailOutboxStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "exported_at")
    private Instant exportedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public byte[] getRecipientAddressEncrypted() {
        return recipientAddressEncrypted;
    }

    public void setRecipientAddressEncrypted(byte[] recipientAddressEncrypted) {
        this.recipientAddressEncrypted = recipientAddressEncrypted;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public String getEmlData() {
        return emlData;
    }

    public void setEmlData(String emlData) {
        this.emlData = emlData;
    }

    public EmailOutboxStatus getStatus() {
        return status;
    }

    public void setStatus(EmailOutboxStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(Instant exportedAt) {
        this.exportedAt = exportedAt;
    }
}
