-- ============================================================
-- V5__dispute_placeholder_anonymization.sql
-- Make the user-deletion path ("hard-delete after 30 days unless OPEN dispute") work for
-- users who have RESOLVED or DISMISSED disputes too. The original schema in V1 left
-- fk_dispute_user without an ON DELETE clause, so MySQL's default RESTRICT silently
-- blocked deletion for ANY historical dispute and the audit policy drifted to "any
-- dispute, ever, blocks deletion" — which contradicts the documented exemption.
--
-- Strategy: preserve dispute history for forensics, but anonymize it when the user is
-- erased. Disputes get a NEW placeholder_id column pointing at deleted_user_placeholder;
-- AuditService writes that link before deleting the user, then user_id is nulled by the
-- ON DELETE SET NULL action below. Open disputes still block deletion in the service —
-- this migration only enables the closed-dispute path.
-- ============================================================

ALTER TABLE dispute
    ADD COLUMN placeholder_id BIGINT NULL AFTER user_id,
    ADD INDEX idx_dispute_placeholder (placeholder_id),
    ADD CONSTRAINT fk_dispute_placeholder FOREIGN KEY (placeholder_id)
        REFERENCES deleted_user_placeholder (id) ON DELETE SET NULL;

-- Allow user_id to be nullified once the user is hard-deleted.
ALTER TABLE dispute MODIFY COLUMN user_id BIGINT NULL;

-- Replace the RESTRICT FK with SET NULL so the cascade works even if the service-layer
-- anonymization step is skipped (defense in depth).
ALTER TABLE dispute DROP FOREIGN KEY fk_dispute_user;
ALTER TABLE dispute
    ADD CONSTRAINT fk_dispute_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE SET NULL;
