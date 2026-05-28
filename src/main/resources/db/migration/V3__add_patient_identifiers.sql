-- =============================================================================
-- V3__add_patient_identifiers.sql
-- PneumaCare — Replace flat national_id with structured patient_identifiers
--
-- Motivation
-- ----------
-- The original patient_identities.national_id column modelled all identifier
-- types (DNI, CUIL, CUIT, Passport…) as a single flat text field. This
-- migration replaces that design with two new tables:
--
--   patient_identifier_types  — catalog (DNI, CUIL, …); not PII
--   patient_identifiers       — one encrypted value per identifier per patient
--
-- The national_id column is dropped. Any existing national_id data is
-- intentionally not migrated — this migration targets dev/test environments
-- where no production data exists yet.
--
-- PII
-- ---
-- patient_identifiers.patient_identifier_name stores the raw identifier value
-- (e.g. "12345678") encrypted with AES-256-GCM via JPA AesAttributeConverter.
-- The identifier type name (e.g. "DNI") is a catalog value and is NOT PII.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Identifier type catalog (not PII)
-- ---------------------------------------------------------------------------

CREATE TABLE patient_identifier_types (
    patient_identifier_type_id          SERIAL       NOT NULL,
    patient_identifier_type_name        VARCHAR(50)  NOT NULL,
    patient_identifier_type_description VARCHAR(255),
    CONSTRAINT pk_patient_identifier_types      PRIMARY KEY (patient_identifier_type_id),
    CONSTRAINT uq_patient_identifier_type_name  UNIQUE      (patient_identifier_type_name)
);

COMMENT ON TABLE  patient_identifier_types IS
    'Catalog of identifier types (DNI, CUIL, CUIT, Passport, etc.). Not PII.';
COMMENT ON COLUMN patient_identifier_types.patient_identifier_type_name IS
    'Short code shown in the UI, e.g. DNI, CUIL, CUIT, Pasaporte.';

-- ---------------------------------------------------------------------------
-- 2. Encrypted identifier values (PII)
-- ---------------------------------------------------------------------------

CREATE TABLE patient_identifiers (
    patient_identifier_id      SERIAL NOT NULL,
    patient_identity_id        UUID   NOT NULL,
    patient_identifier_type_id INT    NOT NULL,
    patient_identifier_name    TEXT   NOT NULL,   -- [PII][ENCRYPTED]
    CONSTRAINT pk_patient_identifiers          PRIMARY KEY (patient_identifier_id),
    CONSTRAINT fk_patient_identifiers_identity FOREIGN KEY (patient_identity_id)
        REFERENCES patient_identities (id) ON DELETE CASCADE,
    CONSTRAINT fk_patient_identifiers_type     FOREIGN KEY (patient_identifier_type_id)
        REFERENCES patient_identifier_types (patient_identifier_type_id)
);

COMMENT ON TABLE  patient_identifiers IS
    '[PII TABLE] One row per identifier per patient. Values are AES-256-GCM encrypted.';
COMMENT ON COLUMN patient_identifiers.patient_identifier_name IS
    '[PII][ENCRYPTED] AES-256-GCM encrypted identifier value. '
    'Format: Base64(IV[12 bytes] || ciphertext+authTag). Plain text via JPA AesAttributeConverter.';

CREATE INDEX idx_patient_identifiers_identity ON patient_identifiers (patient_identity_id);
CREATE INDEX idx_patient_identifiers_type     ON patient_identifiers (patient_identifier_type_id);

-- ---------------------------------------------------------------------------
-- 3. Remove the old flat national_id column
--    (superseded by patient_identifiers; no data migration needed at this stage)
-- ---------------------------------------------------------------------------

ALTER TABLE patient_identities     DROP COLUMN IF EXISTS national_id;
ALTER TABLE patient_identities_aud DROP COLUMN IF EXISTS national_id;
