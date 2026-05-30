-- =============================================================================
-- V4__seed_identifier_types.sql
-- PneumaCare — Seed standard Argentine patient identifier types
--
-- Inserts the initial catalog rows into patient_identifier_types.
-- The UNIQUE constraint on patient_identifier_type_name (added in V3)
-- guarantees this migration is idempotent if re-run against an already-seeded
-- schema (though Flyway's checksum tracking prevents double execution in
-- normal operation).
--
-- Identifier types are not PII — they are generic labels, not personal data.
-- =============================================================================

INSERT INTO patient_identifier_types (patient_identifier_type_name, patient_identifier_type_description)
VALUES
    ('DNI',       'Documento Nacional de Identidad'),
    ('CUIL',      'Código Único de Identificación Laboral'),
    ('CUIT',      'Código Único de Identificación Tributaria'),
    ('LE',        'Libreta de Enrolamiento'),
    ('LC',        'Libreta Cívica'),
    ('Pasaporte', 'Pasaporte');
