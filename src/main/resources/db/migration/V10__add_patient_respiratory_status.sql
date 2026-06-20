-- =============================================================================
-- V10__add_patient_respiratory_status.sql
-- PNMC-94 — Airway Events Management
--
-- Adds the airway/respiratory state to patients. This is ORTHOGONAL to the
-- existing clinical_status (admission lifecycle: ADMITTED/DISCHARGED/TRANSFERRED).
-- respiratory_status is driven by the airway-event state machine
-- (SPONTANEOUS <-> INTUBATED -> TRACHEOSTOMY).
--
-- Runs only in staging/prod (Flyway disabled in dev; dev uses ddl-auto:update).
-- =============================================================================

ALTER TABLE patients
    ADD COLUMN respiratory_status VARCHAR(50) NOT NULL DEFAULT 'SPONTANEOUS';

COMMENT ON COLUMN patients.respiratory_status IS
    'Airway state (SPONTANEOUS/INTUBATED/TRACHEOSTOMY), driven by airway_events. Orthogonal to clinical_status (admission lifecycle).';

CREATE INDEX idx_patients_respiratory_status ON patients (respiratory_status);