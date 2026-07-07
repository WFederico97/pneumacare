-- =============================================================================
-- V21__add_director_role.sql
-- PneumaCare - Add ROLE_DIRECTOR to the user_roles CHECK constraint.
--
-- Introduces the executive "Hospital Director" role. The Role enum is the source
-- of role strings; this keeps the user_roles.role CHECK in sync (staging/prod).
-- =============================================================================

ALTER TABLE user_roles DROP CONSTRAINT ck_user_roles_role;

ALTER TABLE user_roles ADD CONSTRAINT ck_user_roles_role CHECK (role IN
    ('ROLE_ADMIN', 'ROLE_CHIEF_OF_GUARD', 'ROLE_THERAPIST', 'ROLE_COMPLIANCE', 'ROLE_DIRECTOR'));
