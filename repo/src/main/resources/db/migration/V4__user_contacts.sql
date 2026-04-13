-- ============================================================
-- V4 — User contacts (first-class managed profile feature)
-- ============================================================
-- The prompt calls for "maintain contacts and US-style addresses" on profiles. V1 only
-- covered addresses; this migration adds a dedicated contact model so users can store
-- emergency / guardian / office / etc. contacts alongside their own profile. PII fields
-- (name, email, phone, notes) are encrypted at rest using the same AES-256-GCM scheme
-- as other PII columns. phone_last4 is kept in plaintext purely as a lookup hint (the
-- last 4 digits are considered low-sensitivity and already used elsewhere).

CREATE TABLE user_contact (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    label           VARCHAR(60)  NOT NULL,
    relationship    VARCHAR(60)  NULL,
    name_encrypted  VARBINARY(512) NULL,
    email_encrypted VARBINARY(512) NULL,
    phone_encrypted VARBINARY(512) NULL,
    phone_last4     VARCHAR(4)   NULL,
    notes_encrypted VARBINARY(2048) NULL,
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_contact_user (user_id),
    CONSTRAINT fk_user_contact_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
