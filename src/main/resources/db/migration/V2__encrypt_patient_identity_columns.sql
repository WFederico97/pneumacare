-- =============================================================================
-- V2__encrypt_patient_identity_columns.sql
-- PneumaCare — Prepare patient_identities for AES-256-GCM column encryption
--
-- Context
-- -------
-- AES-256-GCM with a random 12-byte IV produces ciphertext that is longer than
-- the original plaintext. The encoded storage format is:
--
--   Base64( IV[12 bytes] || AES-GCM-Ciphertext+AuthTag )
--
-- For a 100-character input this yields ~172 Base64 characters.
-- The original VARCHAR(255) columns are sufficient for typical inputs, but
-- TEXT eliminates any truncation risk for unusually long values and is the
-- correct type for opaque encrypted blobs.
--
-- UNIQUE constraint removal
-- -------------------------
-- AES-GCM uses a random IV per write: the same plaintext encrypts to a
-- DIFFERENT ciphertext on every INSERT. A DB-level UNIQUE constraint on an
-- encrypted column cannot detect duplicate plaintexts and is therefore dropped.
-- Application-layer dedup via an equality query is equally impossible — the
-- query parameter would be re-encrypted to a new ciphertext, never matching
-- stored rows. Enforcing uniqueness requires a deterministic auxiliary column
-- (e.g., HMAC-SHA256 of the plaintext) with its own UNIQUE index.
-- This is currently deferred — duplicate national IDs are not prevented.
-- =============================================================================

-- Widen encrypted columns to TEXT so any realistic input fits after encryption
ALTER TABLE patient_identities ALTER COLUMN first_name  TYPE TEXT;
ALTER TABLE patient_identities ALTER COLUMN last_name   TYPE TEXT;
ALTER TABLE patient_identities ALTER COLUMN national_id TYPE TEXT;

-- Drop DB-level uniqueness — random-IV GCM encryption makes it meaningless
ALTER TABLE patient_identities DROP CONSTRAINT IF EXISTS uq_patient_identities_nat_id;

COMMENT ON COLUMN patient_identities.first_name  IS
    '[PII][ENCRYPTED] AES-256-GCM encrypted. Format: Base64(IV[12] || ciphertext+tag). Plain text via JPA AesAttributeConverter.';
COMMENT ON COLUMN patient_identities.last_name   IS
    '[PII][ENCRYPTED] AES-256-GCM encrypted. Format: Base64(IV[12] || ciphertext+tag). Plain text via JPA AesAttributeConverter.';
COMMENT ON COLUMN patient_identities.national_id IS
    '[PII][ENCRYPTED] AES-256-GCM encrypted. UNIQUE constraint removed — random IV makes DB-level dedup impossible. Uniqueness enforcement (HMAC) is deferred.';
