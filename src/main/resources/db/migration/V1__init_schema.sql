-- =============================================================================
-- V1__init_schema.sql
-- PneumaCare — Initial relational schema
-- Source: PNEUMACARE-DER-V1.0.0
--
-- Table creation order respects FK dependencies (parents before children).
-- TIMESTAMP columns use TIMESTAMPTZ (timezone-aware) as PostgreSQL best
-- practice; semantically equivalent to the TIMESTAMP shown in the DER.
--
-- PII isolation strategy:
--   patient_identities  — all identity fields ([PII] columns, future encryption)
--   patients            — operational record only; no PII stored directly
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Geographic / Organizational Hierarchy
-- ---------------------------------------------------------------------------

CREATE TABLE provinces (
    id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    name   VARCHAR(100) NOT NULL,
    region VARCHAR(50),
    CONSTRAINT pk_provinces PRIMARY KEY (id)
);

CREATE TABLE hospitals (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    province_id        UUID         NOT NULL,
    name               VARCHAR(150) NOT NULL,
    institutional_type VARCHAR(50),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_hospitals          PRIMARY KEY (id),
    CONSTRAINT fk_hospitals_province FOREIGN KEY (province_id) REFERENCES provinces (id)
);

CREATE TABLE intensive_care_units (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    hospital_id UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_intensive_care_units         PRIMARY KEY (id),
    CONSTRAINT uq_intensive_care_units_code    UNIQUE (code),
    CONSTRAINT fk_intensive_care_units_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
);

-- ---------------------------------------------------------------------------
-- 2. Users & Roles (RBAC)
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id   UUID        NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    CONSTRAINT pk_roles     PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    hospital_id   UUID         NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_users           PRIMARY KEY (id),
    CONSTRAINT uq_users_username  UNIQUE (username),
    CONSTRAINT fk_users_hospital  FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles       PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role  FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- 3. ICU Infrastructure
-- ---------------------------------------------------------------------------

CREATE TABLE ventilator_models (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    brand            VARCHAR(50),
    model            VARCHAR(100) NOT NULL,
    software_version VARCHAR(50),
    CONSTRAINT pk_ventilator_models PRIMARY KEY (id)
);

CREATE TABLE physical_ventilators (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    icu_id        UUID         NOT NULL,
    model_id      UUID         NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT pk_physical_ventilators        PRIMARY KEY (id),
    CONSTRAINT uq_physical_ventilators_serial UNIQUE (serial_number),
    CONSTRAINT fk_physical_ventilators_icu    FOREIGN KEY (icu_id)    REFERENCES intensive_care_units (id),
    CONSTRAINT fk_physical_ventilators_model  FOREIGN KEY (model_id)  REFERENCES ventilator_models (id)
);

CREATE TABLE icu_beds (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    icu_id     UUID        NOT NULL,
    bed_number VARCHAR(50) NOT NULL,
    status     VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT pk_icu_beds        PRIMARY KEY (id),
    CONSTRAINT uq_icu_beds_number UNIQUE (icu_id, bed_number),
    CONSTRAINT fk_icu_beds_icu    FOREIGN KEY (icu_id) REFERENCES intensive_care_units (id)
);

-- ---------------------------------------------------------------------------
-- 4. Patient Identity [PII] — strictly isolated from clinical data
--    Columns tagged [PII] are candidates for AES-256 column-level encryption
--    via a JPA AttributeConverter before any staging/production deployment.
-- ---------------------------------------------------------------------------

CREATE TABLE patient_identities (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name  VARCHAR(255) NOT NULL,  -- [PII]
    last_name   VARCHAR(255) NOT NULL,  -- [PII]
    national_id VARCHAR(255) NOT NULL,  -- [PII]
    birth_date  DATE         NOT NULL,  -- [PII]
    CONSTRAINT pk_patient_identities        PRIMARY KEY (id),
    CONSTRAINT uq_patient_identities_nat_id UNIQUE (national_id)
);

COMMENT ON TABLE  patient_identities            IS '[PII TABLE] Personally identifiable information. Strictly isolated from clinical records. All columns are candidates for column-level encryption.';
COMMENT ON COLUMN patient_identities.first_name  IS '[PII] — candidate for AES-256 column encryption via JPA AttributeConverter.';
COMMENT ON COLUMN patient_identities.last_name   IS '[PII] — candidate for AES-256 column encryption via JPA AttributeConverter.';
COMMENT ON COLUMN patient_identities.national_id IS '[PII] National document number (DNI/CUIL) — candidate for AES-256 column encryption.';
COMMENT ON COLUMN patient_identities.birth_date  IS '[PII] — candidate for encryption.';

-- ---------------------------------------------------------------------------
-- 5. Patients — operational record only; PII lives in patient_identities
-- ---------------------------------------------------------------------------

CREATE TABLE patients (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    icu_id          UUID        NOT NULL,
    identity_id     UUID        NOT NULL,
    bed_id          UUID,
    clinical_status VARCHAR(50) NOT NULL DEFAULT 'ADMITTED',
    admission_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_patients          PRIMARY KEY (id),
    CONSTRAINT fk_patients_icu      FOREIGN KEY (icu_id)      REFERENCES intensive_care_units (id),
    CONSTRAINT fk_patients_identity FOREIGN KEY (identity_id) REFERENCES patient_identities (id),
    CONSTRAINT fk_patients_bed      FOREIGN KEY (bed_id)      REFERENCES icu_beds (id) ON DELETE SET NULL
);

COMMENT ON TABLE  patients            IS 'Operational patient record. No PII stored — identity linked via identity_id to patient_identities.';
COMMENT ON COLUMN patients.identity_id IS 'FK to patient_identities ([PII] table). The only link between operational data and PII.';

-- ---------------------------------------------------------------------------
-- 6. Shift Management
-- ---------------------------------------------------------------------------

CREATE TABLE medical_shifts (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    icu_id        UUID        NOT NULL,
    chief_user_id UUID        NOT NULL,
    start_time    TIMESTAMPTZ NOT NULL,
    end_time      TIMESTAMPTZ,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT pk_medical_shifts       PRIMARY KEY (id),
    CONSTRAINT fk_medical_shifts_icu   FOREIGN KEY (icu_id)        REFERENCES intensive_care_units (id),
    CONSTRAINT fk_medical_shifts_chief FOREIGN KEY (chief_user_id) REFERENCES users (id)
);

CREATE TABLE shift_handovers (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    shift_id                UUID        NOT NULL,
    incoming_notes          TEXT,
    outgoing_notes          TEXT,
    critical_events_summary TEXT,
    closed_at               TIMESTAMPTZ,
    CONSTRAINT pk_shift_handovers       PRIMARY KEY (id),
    CONSTRAINT fk_shift_handovers_shift FOREIGN KEY (shift_id) REFERENCES medical_shifts (id)
);

CREATE TABLE clinical_assignments (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    shift_id          UUID        NOT NULL,
    therapist_user_id UUID        NOT NULL,
    patient_id        UUID        NOT NULL,
    assigned_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at     TIMESTAMPTZ,
    CONSTRAINT pk_clinical_assignments             PRIMARY KEY (id),
    CONSTRAINT fk_clinical_assignments_shift       FOREIGN KEY (shift_id)          REFERENCES medical_shifts (id),
    CONSTRAINT fk_clinical_assignments_therapist   FOREIGN KEY (therapist_user_id) REFERENCES users (id),
    CONSTRAINT fk_clinical_assignments_patient     FOREIGN KEY (patient_id)        REFERENCES patients (id)
);

-- ---------------------------------------------------------------------------
-- 7. Clinical Evaluations — no PII; linked to patients via UUID FK only
-- ---------------------------------------------------------------------------

CREATE TABLE evaluations (
    id                     UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id             UUID        NOT NULL,
    shift_id               UUID        NOT NULL,
    physical_ventilator_id UUID        NOT NULL,
    evaluation_time        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Respiratory mechanics
    f                      NUMERIC(5,2),   -- respiratory rate (breaths/min) — input to RSBI
    vt                     NUMERIC(8,2),   -- tidal volume (mL)              — input to RSBI & Cstat
    -- Oxygenation
    pao2                   NUMERIC(5,2),   -- partial pressure O2 (mmHg)     — input to PaFi
    fio2                   NUMERIC(3,2),   -- fraction of inspired O2         — input to PaFi
    -- Ventilatory pressures
    pplat                  NUMERIC(5,2),   -- plateau pressure (cmH2O)        — input to Cstat
    peep                   NUMERIC(5,2),   -- positive end-expiratory pressure (cmH2O)
    -- Extended / free-form parameters
    extended_parameters    JSONB,
    -- Computed clinical indices — stored for auditability and trend reporting
    rsbi_snapshot          NUMERIC(8,2),   -- f / (Vt/1000)  breaths·min⁻¹·L⁻¹
    pafi_snapshot          NUMERIC(8,2),   -- PaO2 / FiO2    mmHg
    cstat_snapshot         NUMERIC(8,2),   -- Vt / (Pplat-PEEP)  mL/cmH2O
    alert_triggered        BOOLEAN         NOT NULL DEFAULT false,
    created_by             UUID            NOT NULL,
    CONSTRAINT pk_evaluations               PRIMARY KEY (id),
    CONSTRAINT fk_evaluations_patient       FOREIGN KEY (patient_id)             REFERENCES patients (id),
    CONSTRAINT fk_evaluations_shift         FOREIGN KEY (shift_id)               REFERENCES medical_shifts (id),
    CONSTRAINT fk_evaluations_ventilator    FOREIGN KEY (physical_ventilator_id) REFERENCES physical_ventilators (id),
    CONSTRAINT fk_evaluations_created_by    FOREIGN KEY (created_by)             REFERENCES users (id),
    CONSTRAINT ck_evaluations_fio2          CHECK (fio2 BETWEEN 0.21 AND 1.0),
    CONSTRAINT ck_evaluations_f             CHECK (f    BETWEEN 0    AND 80),
    CONSTRAINT ck_evaluations_pao2          CHECK (pao2 BETWEEN 0    AND 700)
);

COMMENT ON TABLE  evaluations                    IS 'Clinical evaluation records. No PII — patient linked via UUID FK only.';
COMMENT ON COLUMN evaluations.f                  IS 'Respiratory rate (breaths/min). Input to RSBI = f / (Vt/1000).';
COMMENT ON COLUMN evaluations.vt                 IS 'Tidal volume in mL. Input to RSBI and Cstat calculations.';
COMMENT ON COLUMN evaluations.rsbi_snapshot      IS 'Persisted RSBI result for audit and historical trend queries.';
COMMENT ON COLUMN evaluations.pafi_snapshot      IS 'Persisted PaFi (Horowitz index) result for audit.';
COMMENT ON COLUMN evaluations.cstat_snapshot     IS 'Persisted static compliance result for audit.';
COMMENT ON COLUMN evaluations.extended_parameters IS 'Free-form JSONB for ventilator parameters not yet modelled as columns.';

-- ---------------------------------------------------------------------------
-- 8. Spontaneous Breathing Trials
-- ---------------------------------------------------------------------------

CREATE TABLE spontaneous_breathing_trials (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id       UUID        NOT NULL,
    shift_id         UUID        NOT NULL,
    start_time       TIMESTAMPTZ NOT NULL,
    end_time         TIMESTAMPTZ,
    duration_minutes INT,
    trial_mode       VARCHAR(50),
    outcome          VARCHAR(20),
    failure_reason   VARCHAR(100),
    created_by       UUID        NOT NULL,
    CONSTRAINT pk_spontaneous_breathing_trials  PRIMARY KEY (id),
    CONSTRAINT fk_sbt_patient                   FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_sbt_shift                     FOREIGN KEY (shift_id)   REFERENCES medical_shifts (id),
    CONSTRAINT fk_sbt_created_by                FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- 9. Airway Assessments
-- ---------------------------------------------------------------------------

CREATE TABLE airway_assessments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id       UUID        NOT NULL,
    shift_id         UUID        NOT NULL,
    assessment_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    secretion_volume VARCHAR(20),
    secretion_aspect VARCHAR(20),
    cough_strength   VARCHAR(20),
    cuff_pressure    NUMERIC(5,2),
    created_by       UUID        NOT NULL,
    CONSTRAINT pk_airway_assessments         PRIMARY KEY (id),
    CONSTRAINT fk_airway_assessments_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_airway_assessments_shift   FOREIGN KEY (shift_id)   REFERENCES medical_shifts (id),
    CONSTRAINT fk_airway_assessments_by      FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- 10. Airway Events
-- ---------------------------------------------------------------------------

CREATE TABLE airway_events (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id          UUID        NOT NULL,
    shift_id            UUID        NOT NULL,
    event_time          TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type          VARCHAR(20),
    tube_size           NUMERIC(3,1),
    is_successful       BOOLEAN,
    complications_noted TEXT,
    created_by          UUID        NOT NULL,
    CONSTRAINT pk_airway_events         PRIMARY KEY (id),
    CONSTRAINT fk_airway_events_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_airway_events_shift   FOREIGN KEY (shift_id)   REFERENCES medical_shifts (id),
    CONSTRAINT fk_airway_events_by      FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- 11. Arterial Blood Gases
-- ---------------------------------------------------------------------------

CREATE TABLE arterial_blood_gases (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id  UUID        NOT NULL,
    sample_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    ph          NUMERIC(3,2),
    pco2        NUMERIC(5,2),
    po2         NUMERIC(5,2),
    hco3        NUMERIC(5,2),
    lactate     NUMERIC(5,2),
    created_by  UUID        NOT NULL,
    CONSTRAINT pk_arterial_blood_gases         PRIMARY KEY (id),
    CONSTRAINT fk_arterial_blood_gases_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_arterial_blood_gases_by      FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------------
-- 12. Patient Consents
-- ---------------------------------------------------------------------------

CREATE TABLE patient_consents (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    patient_id   UUID         NOT NULL,
    consent_type VARCHAR(100) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    accepted_at  TIMESTAMPTZ,
    ip_address   VARCHAR(45),
    CONSTRAINT pk_patient_consents         PRIMARY KEY (id),
    CONSTRAINT fk_patient_consents_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
);

-- ---------------------------------------------------------------------------
-- 13. Clinical Alerts Log
-- ---------------------------------------------------------------------------

CREATE TABLE clinical_alerts_log (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    evaluation_id   UUID        NOT NULL,
    alert_type      VARCHAR(50) NOT NULL,
    payload_sent    TEXT,
    dispatched_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivery_status VARCHAR(20),
    CONSTRAINT pk_clinical_alerts_log      PRIMARY KEY (id),
    CONSTRAINT fk_clinical_alerts_log_eval FOREIGN KEY (evaluation_id) REFERENCES evaluations (id)
);

-- ---------------------------------------------------------------------------
-- 14. AI Clinical Insights
-- ---------------------------------------------------------------------------

CREATE TABLE ai_clinical_insights (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    evaluation_id     UUID        NOT NULL,
    prompt_hash       VARCHAR(64),
    generated_insight TEXT,
    confidence_score  NUMERIC(3,2),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_clinical_insights       PRIMARY KEY (id),
    CONSTRAINT fk_ai_clinical_insights_eval  FOREIGN KEY (evaluation_id) REFERENCES evaluations (id),
    CONSTRAINT ck_ai_clinical_insights_score CHECK (confidence_score BETWEEN 0 AND 1)
);

-- ---------------------------------------------------------------------------
-- 15. Audit — Hibernate Envers
--     audit_revisions: custom @RevisionEntity table.
--     *_aud: shadow tables for @Audited entities.
--     Composite PK (id, rev) on shadow tables is the Envers standard.
-- ---------------------------------------------------------------------------

CREATE TABLE audit_revisions (
    id         BIGSERIAL   NOT NULL,
    timestamp  BIGINT      NOT NULL,
    user_id    UUID,
    ip_address VARCHAR(45),
    CONSTRAINT pk_audit_revisions PRIMARY KEY (id)
);

CREATE TABLE patient_identities_aud (
    id          UUID   NOT NULL,
    rev         BIGINT NOT NULL,
    revtype     INT,
    first_name  VARCHAR(255),
    last_name   VARCHAR(255),
    national_id VARCHAR(255),
    birth_date  DATE,
    CONSTRAINT pk_patient_identities_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_patient_identities_aud_rev FOREIGN KEY (rev) REFERENCES audit_revisions (id)
);

CREATE TABLE patients_aud (
    id              UUID   NOT NULL,
    rev             BIGINT NOT NULL,
    revtype         INT,
    icu_id          UUID,
    identity_id     UUID,
    bed_id          UUID,
    clinical_status VARCHAR(50),
    admission_date  TIMESTAMPTZ,
    CONSTRAINT pk_patients_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_patients_aud_rev FOREIGN KEY (rev) REFERENCES audit_revisions (id)
);

CREATE TABLE evaluations_aud (
    id                     UUID   NOT NULL,
    rev                    BIGINT NOT NULL,
    revtype                INT,
    patient_id             UUID,
    shift_id               UUID,
    physical_ventilator_id UUID,
    evaluation_time        TIMESTAMPTZ,
    f                      NUMERIC(5,2),
    vt                     NUMERIC(8,2),
    pao2                   NUMERIC(5,2),
    fio2                   NUMERIC(3,2),
    pplat                  NUMERIC(5,2),
    peep                   NUMERIC(5,2),
    extended_parameters    JSONB,
    rsbi_snapshot          NUMERIC(8,2),
    pafi_snapshot          NUMERIC(8,2),
    cstat_snapshot         NUMERIC(8,2),
    alert_triggered        BOOLEAN,
    CONSTRAINT pk_evaluations_aud     PRIMARY KEY (id, rev),
    CONSTRAINT fk_evaluations_aud_rev FOREIGN KEY (rev) REFERENCES audit_revisions (id)
);

-- ---------------------------------------------------------------------------
-- 16. Indexes
-- ---------------------------------------------------------------------------

-- Organizational hierarchy
CREATE INDEX idx_hospitals_province           ON hospitals (province_id);
CREATE INDEX idx_icu_hospital                 ON intensive_care_units (hospital_id);
CREATE INDEX idx_users_hospital               ON users (hospital_id);

-- Infrastructure
CREATE INDEX idx_physical_ventilators_icu     ON physical_ventilators (icu_id);
CREATE INDEX idx_icu_beds_icu                 ON icu_beds (icu_id);

-- Patients
CREATE INDEX idx_patients_icu                 ON patients (icu_id);
CREATE INDEX idx_patients_identity            ON patients (identity_id);
CREATE INDEX idx_patients_bed                 ON patients (bed_id);
CREATE INDEX idx_patients_status              ON patients (clinical_status);

-- Shifts & assignments
CREATE INDEX idx_medical_shifts_icu           ON medical_shifts (icu_id);
CREATE INDEX idx_clinical_assignments_shift   ON clinical_assignments (shift_id);
CREATE INDEX idx_clinical_assignments_patient ON clinical_assignments (patient_id);

-- Evaluations — most queried table
CREATE INDEX idx_evaluations_patient          ON evaluations (patient_id);
CREATE INDEX idx_evaluations_shift            ON evaluations (shift_id);
CREATE INDEX idx_evaluations_time             ON evaluations (evaluation_time DESC);

-- Clinical sub-tables
CREATE INDEX idx_sbt_patient                  ON spontaneous_breathing_trials (patient_id);
CREATE INDEX idx_airway_assessments_patient   ON airway_assessments (patient_id);
CREATE INDEX idx_airway_events_patient        ON airway_events (patient_id);
CREATE INDEX idx_abg_patient                  ON arterial_blood_gases (patient_id);
CREATE INDEX idx_clinical_alerts_evaluation   ON clinical_alerts_log (evaluation_id);
CREATE INDEX idx_ai_insights_evaluation       ON ai_clinical_insights (evaluation_id);
