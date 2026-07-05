-- =============================================================================
-- V19__create_asset_assignments.sql
-- PneumaCare - Hardware assignment history linking a physical ventilator to a
-- patient. Active assignment = released_at IS NULL. Partial unique indexes keep
-- at most one active assignment per ventilator and per patient.
-- =============================================================================

CREATE TABLE asset_assignments (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    ventilator_id UUID        NOT NULL,
    patient_id    UUID        NOT NULL,
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at   TIMESTAMPTZ,
    assigned_by   UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT pk_asset_assignments            PRIMARY KEY (id),
    CONSTRAINT fk_asset_assignments_ventilator FOREIGN KEY (ventilator_id) REFERENCES physical_ventilators (id),
    CONSTRAINT fk_asset_assignments_patient    FOREIGN KEY (patient_id)    REFERENCES patients (id)
);

COMMENT ON TABLE asset_assignments IS
    'History log of ventilator-to-patient assignments; active row has released_at IS NULL.';

-- One active assignment per ventilator, and one active ventilator per patient.
CREATE UNIQUE INDEX uq_asset_assignments_active_ventilator
    ON asset_assignments (ventilator_id) WHERE released_at IS NULL;
CREATE UNIQUE INDEX uq_asset_assignments_active_patient
    ON asset_assignments (patient_id) WHERE released_at IS NULL;

-- Supports the active-assignment lookup on unassign.
CREATE INDEX idx_asset_assignments_ventilator ON asset_assignments (ventilator_id);
