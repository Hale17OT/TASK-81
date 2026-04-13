-- ============================================================
-- V3 — Force password rotation on first login for seed accounts
-- ============================================================
-- Adds the password_change_required flag and sets it TRUE for every user that was
-- created via the V2 seed script. This closes the "published default credentials"
-- exposure: seeded admin/teacher/student accounts can authenticate once with the
-- documented bootstrap password, but will be routed to /account/change-password
-- before any other page renders or any privileged API can be called. The flag is
-- cleared by a successful self-service password change.

ALTER TABLE `user`
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

-- All pre-existing seed accounts must rotate before proceeding.
UPDATE `user`
   SET password_change_required = TRUE
 WHERE username IN ('admin', 'teacher1', 'teacher2', 'student1', 'student2', 'student3');
