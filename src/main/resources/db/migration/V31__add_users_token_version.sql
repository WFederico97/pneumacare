-- =============================================================================
-- V31__add_users_token_version.sql
-- Session invalidation for self-issued JWTs.
--
-- The token carries this value as a claim; the request filter rejects a token
-- whose claim no longer matches the stored column. Bumping the column therefore
-- invalidates every session already issued for that user — which is what makes
-- a password change end sessions open on other devices, instead of leaving them
-- alive until the token expires.
--
-- Existing users start at 0; tokens issued before this migration carry no claim
-- and are rejected, so everyone signs in once after deploy.
--
-- Runs only in staging/prod (Flyway disabled in dev).
-- =============================================================================

ALTER TABLE users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
