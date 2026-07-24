-- =============================================================================
-- V6__domain_integrity_constraints.sql
-- PneumaCare — Domain integrity hardening
--
-- Addresses all domain integrity gaps identified in the schema review:
--
--   1. hospitals.institutional_type_id  — enforce NOT NULL
--   2. patient_identifiers              — UNIQUE (identity, type) — no duplicate
--                                         identifier types per patient
--   3. Status VARCHAR columns           — CHECK constraints on 7 tables
--   4. evaluations                      — clinical range checks (vt, peep, pplat)
--   5. Temporal consistency             — end_time > start_time on shifts and SBTs
--   6. patient_identities.birth_date    — must not be a future date
--   7. clinical_assignments             — partial unique index: one active
--                                         assignment per patient per shift
--   8. Categorical vocabulary columns  — CHECK constraints on clinical sub-tables
--
-- Assumptions
-- -----------
-- All existing rows satisfy the new constraints. If any row violates a
-- constraint, ALTER TABLE will fail and the migration must be preceded by a
-- data-correction script. For new (empty) databases this is never an issue.
-- =============================================================================


-- ---------------------------------------------------------------------------
-- 1. hospitals.institutional_type_id — NOT NULL
--    Every hospital must declare its institutional type after V5 introduced
--    the reference table. Any remaining NULL rows are assigned the default
--    type 'PÚBLICO' before the constraint is applied.
-- ---------------------------------------------------------------------------

UPDATE hospitals
SET    institutional_type_id = (SELECT id FROM institutional_types WHERE name = 'PÚBLICO')
WHERE  institutional_type_id IS NULL;

ALTER TABLE hospitals
    ALTER COLUMN institutional_type_id SET NOT NULL;


-- ---------------------------------------------------------------------------
-- 2. patient_identifiers — UNIQUE (patient_identity_id, patient_identifier_type_id)
--    A patient may hold at most one value of each identifier type
--    (e.g. exactly one DNI, at most one CUIL).
-- ---------------------------------------------------------------------------

ALTER TABLE patient_identifiers
    ADD CONSTRAINT uq_patient_identifiers_identity_type
        UNIQUE (patient_identity_id, patient_identifier_type_id);


-- ---------------------------------------------------------------------------
-- 3. Status CHECK constraints
--    All status-like VARCHAR columns must accept only the defined domain values.
--    This enforces the same rules as the Java enums but at the database layer,
--    protecting against direct SQL writes and future clients that bypass the JPA.
-- ---------------------------------------------------------------------------

-- users
ALTER TABLE users
    ADD CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));

-- physical_ventilators — status CHECK is added by V17 to match the
-- VentilatorStatus enum (AVAILABLE, IN_USE, MAINTENANCE); not defined here.

-- icu_beds  (mirrors BedStatus enum: AVAILABLE, OCCUPIED, MAINTENANCE)
ALTER TABLE icu_beds
    ADD CONSTRAINT ck_icu_beds_status
        CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE'));

-- patients  (mirrors ClinicalStatus enum: ADMITTED, DISCHARGED, TRANSFERRED)
ALTER TABLE patients
    ADD CONSTRAINT ck_patients_clinical_status
        CHECK (clinical_status IN ('ADMITTED', 'DISCHARGED', 'TRANSFERRED'));

-- medical_shifts
ALTER TABLE medical_shifts
    ADD CONSTRAINT ck_medical_shifts_status
        CHECK (status IN ('OPEN', 'CLOSED'));

-- patient_consents
ALTER TABLE patient_consents
    ADD CONSTRAINT ck_patient_consents_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'));

-- clinical_alerts_log — table created by V16 (PNMC-100) with its own status
-- CHECK constraint (PENDING/DELIVERED/FAILED); no additional constraint here.


-- ---------------------------------------------------------------------------
-- 4. evaluations — clinical range checks
--    Three existing CHECKs already cover fio2, f, and pao2.
--    The additions below cover the remaining measurable inputs:
--
--      vt   — tidal volume must be strictly positive; zero would cause
--              division-by-zero in both RSBI (f/Vt) and Cstat (Vt/ΔP).
--      peep — PEEP is non-negative by definition.
--      pplat > peep — required for a meaningful Cstat value; the denominator
--              (pplat − peep) must be positive. NULL in either column makes
--              the CHECK evaluate to NULL, which PostgreSQL treats as passing
--              (constraint is only enforced when both values are present).
-- ---------------------------------------------------------------------------

ALTER TABLE evaluations
    ADD CONSTRAINT ck_evaluations_vt
        CHECK (vt > 0),
    ADD CONSTRAINT ck_evaluations_peep
        CHECK (peep >= 0),
    ADD CONSTRAINT ck_evaluations_pplat_gt_peep
        CHECK (pplat > peep);


-- ---------------------------------------------------------------------------
-- 5. Temporal consistency
--    Closed/completed time must always be later than the opening/start time.
--    The IS NULL guard allows the end time to remain NULL while the record
--    is still open or in progress.
-- ---------------------------------------------------------------------------

ALTER TABLE medical_shifts
    ADD CONSTRAINT ck_medical_shifts_times
        CHECK (end_time IS NULL OR end_time > start_time);

ALTER TABLE spontaneous_breathing_trials
    ADD CONSTRAINT ck_sbt_times
        CHECK (end_time IS NULL OR end_time > start_time),
    ADD CONSTRAINT ck_sbt_duration
        CHECK (duration_minutes IS NULL OR duration_minutes > 0);


-- ---------------------------------------------------------------------------
-- 6. patient_identities.birth_date — must not be a future date
--    Evaluated at INSERT/UPDATE time against CURRENT_DATE. Historical rows
--    loaded via pg_restore are not re-validated (pg_restore disables triggers
--    and defers constraints during COPY phases).
-- ---------------------------------------------------------------------------

ALTER TABLE patient_identities
    ADD CONSTRAINT ck_patient_identities_birth_date
        CHECK (birth_date <= CURRENT_DATE);


-- ---------------------------------------------------------------------------
-- 7. clinical_assignments — one active assignment per patient per shift
--    A plain UNIQUE constraint would block re-assignment after unassignment
--    (historical rows with unassigned_at IS NOT NULL must be allowed to repeat).
--    A partial unique index enforces uniqueness only for active assignments
--    (unassigned_at IS NULL), which is the correct business rule.
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX uq_clinical_assignments_active
    ON clinical_assignments (shift_id, patient_id)
    WHERE unassigned_at IS NULL;


-- ---------------------------------------------------------------------------
-- 8. Categorical vocabulary columns — CHECK constraints
--    These clinical fields carry a small, stable set of values defined by
--    clinical protocol. CHECK is preferred over a reference table because the
--    vocabulary is not user-managed and adding a new value requires a migration
--    review regardless.
-- ---------------------------------------------------------------------------

-- spontaneous_breathing_trials
ALTER TABLE spontaneous_breathing_trials
    ADD CONSTRAINT ck_sbt_trial_mode
        CHECK (trial_mode IN ('CPAP', 'T-PIECE', 'PSV')),
    ADD CONSTRAINT ck_sbt_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE', 'INCOMPLETE'));

-- airway_assessments
ALTER TABLE airway_assessments
    ADD CONSTRAINT ck_airway_assessments_secretion_volume
        CHECK (secretion_volume IN ('ESCASO', 'MODERADO', 'ABUNDANTE')),
    ADD CONSTRAINT ck_airway_assessments_secretion_aspect
        CHECK (secretion_aspect IN ('MUCOSO', 'PURULENTO', 'HEMÁTICO', 'SEROSO')),
    ADD CONSTRAINT ck_airway_assessments_cough_strength
        CHECK (cough_strength IN ('DÉBIL', 'MODERADO', 'FUERTE'));

-- airway_events
ALTER TABLE airway_events
    ADD CONSTRAINT ck_airway_events_event_type
        CHECK (event_type IN ('INTUBACIÓN', 'EXTUBACIÓN', 'REINTUBACIÓN', 'ASPIRACIÓN'));
