-- =============================================================================
-- V15__reshape_users_and_roles.sql
-- PNMC-135 — Users, Roles & RBAC (Auth Foundation)
--
-- Reshapes the V1 user/role schema (an unused DER sketch) into the auth model:
--   * users: drop multi-tenancy (hospital_id) and status; add identity/audit
--            columns (display_name, enabled, created_at, updated_at).
--   * user_roles: replace the normalized role_id FK with a canonical role string
--                 constrained by CHECK to the Role enum values.
--   * roles: dropped — the Role enum is now the single source of role strings.
-- These tables are empty in all environments (authentication is unbuilt), so no
-- data is migrated. Bootstrap admin seeding happens in the app, not here, so no
-- password hash or plaintext appears in this migration.
-- =============================================================================

-- users: drop multi-tenancy + lifecycle status, add identity/audit columns.
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_hospital;
ALTER TABLE users DROP COLUMN hospital_id;
ALTER TABLE users DROP COLUMN status;          -- also drops ck_users_status (V6)

ALTER TABLE users ADD COLUMN display_name VARCHAR(150);
ALTER TABLE users ADD COLUMN enabled      BOOLEAN     NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN created_at   TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE users ADD COLUMN updated_at   TIMESTAMPTZ NOT NULL DEFAULT now();

-- user_roles: replace the role_id FK with a canonical role string.
DROP TABLE user_roles;

CREATE TABLE user_roles (
    user_id UUID        NOT NULL,
    role    VARCHAR(40) NOT NULL,
    CONSTRAINT pk_user_roles      PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_roles_role CHECK (role IN
        ('ROLE_ADMIN', 'ROLE_CHIEF_OF_GUARD', 'ROLE_THERAPIST', 'ROLE_COMPLIANCE'))
);

-- roles table no longer referenced: the Role enum is the single source of truth.
DROP TABLE roles;
