-- =============================================================================
-- V5__add_institutional_types.sql
-- PneumaCare — Introduce institutional_types reference table
--
-- Motivation
-- ----------
-- hospitals.institutional_type was a free VARCHAR(50) with no domain constraint,
-- allowing inconsistent values (e.g. 'PÚBLICO', 'publico', 'Público') that would
-- silently corrupt any report or filter grouping by type.
--
-- This migration replaces the free-text column with a FK to a new reference
-- table, enforcing referential integrity at the database level.
--
-- Migration steps
-- ---------------
-- 1. Create institutional_types catalog table.
-- 2. Seed the initial Argentine institutional type values.
-- 3. Add the nullable institutional_type_id column to hospitals.
-- 4. Backfill institutional_type_id from the existing string column for any
--    rows whose value matches a catalog entry.
-- 5. Add the FK constraint.
-- 6. Drop the old free-text column.
-- 7. Add a covering index for join performance.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Reference table
-- ---------------------------------------------------------------------------

CREATE TABLE institutional_types (
    id   SERIAL      NOT NULL,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_institutional_types PRIMARY KEY (id),
    CONSTRAINT uq_institutional_types UNIQUE (name)
);

COMMENT ON TABLE  institutional_types      IS 'Catalog of hospital institutional types. Not PII.';
COMMENT ON COLUMN institutional_types.name IS 'Short label shown in the UI, e.g. PÚBLICO, PRIVADO.';

-- ---------------------------------------------------------------------------
-- 2. Seed standard Argentine institutional types
-- ---------------------------------------------------------------------------

INSERT INTO institutional_types (name) VALUES
    ('PÚBLICO'),
    ('PRIVADO'),
    ('MIXTO'),
    ('MUNICIPAL'),
    ('NACIONAL');

-- ---------------------------------------------------------------------------
-- 3. Add FK column (nullable during backfill)
-- ---------------------------------------------------------------------------

ALTER TABLE hospitals
    ADD COLUMN institutional_type_id INT;

-- ---------------------------------------------------------------------------
-- 4. Backfill from existing string values
--    Rows whose institutional_type does not match any catalog entry are left
--    NULL; they must be corrected manually before enforcing NOT NULL.
-- ---------------------------------------------------------------------------

UPDATE hospitals h
SET    institutional_type_id = it.id
FROM   institutional_types it
WHERE  it.name = h.institutional_type;

-- ---------------------------------------------------------------------------
-- 5. FK constraint
-- ---------------------------------------------------------------------------

ALTER TABLE hospitals
    ADD CONSTRAINT fk_hospitals_institutional_type
    FOREIGN KEY (institutional_type_id) REFERENCES institutional_types (id);

-- ---------------------------------------------------------------------------
-- 6. Drop obsolete free-text column
-- ---------------------------------------------------------------------------

ALTER TABLE hospitals
    DROP COLUMN institutional_type;

-- ---------------------------------------------------------------------------
-- 7. Index for join performance (type-based filters on hospitals)
-- ---------------------------------------------------------------------------

CREATE INDEX idx_hospitals_institutional_type ON hospitals (institutional_type_id);
