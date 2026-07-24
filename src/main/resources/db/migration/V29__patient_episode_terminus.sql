-- =============================================================================
-- V29: patient episode terminus.
-- The patients row becomes the ICU episode: a person (patient_identities) may
-- have many episodes, at most one open. Closed episodes carry discharge_date
-- + disposition; open episodes carry neither (chk_patients_terminus).
-- =============================================================================

ALTER TABLE patients DROP CONSTRAINT uq_patients_identity;

ALTER TABLE patients
    ADD COLUMN discharge_date TIMESTAMPTZ,
    ADD COLUMN disposition    VARCHAR(50);

-- Closed episodes carry both terminus fields; open episodes neither.
ALTER TABLE patients ADD CONSTRAINT chk_patients_terminus CHECK (
    (clinical_status = 'ADMITTED'  AND discharge_date IS NULL AND disposition IS NULL)
    OR
    (clinical_status <> 'ADMITTED' AND discharge_date IS NOT NULL AND disposition IS NOT NULL)
);

ALTER TABLE patients ADD CONSTRAINT chk_patients_disposition CHECK (
    disposition IS NULL OR disposition IN
    ('HOME', 'WARD', 'TRANSFER_EXTERNAL', 'DECEASED', 'WITHDRAWAL_OF_CARE')
);

-- A person may have many episodes, but at most one open at a time.
CREATE UNIQUE INDEX uq_patients_open_episode
    ON patients (identity_id) WHERE clinical_status = 'ADMITTED';

-- Discharge-window queries (ALOS, turnover, mortality all filter on this).
CREATE INDEX idx_patients_discharge_date ON patients (discharge_date)
    WHERE discharge_date IS NOT NULL;

-- Mirror new columns into the Envers shadow table.
ALTER TABLE patients_aud
    ADD COLUMN discharge_date TIMESTAMPTZ,
    ADD COLUMN disposition    VARCHAR(50);
