-- Enforce: at most one OPEN shift per ICU, correct under concurrency.
-- A partial unique index cannot be expressed via JPA annotations, so it lives here.
CREATE UNIQUE INDEX uq_medical_shifts_one_open_per_icu
    ON medical_shifts (icu_id)
    WHERE status = 'OPEN';