-- Add row-level audit timestamps to medical_shifts (Tier-A mutable entity).
-- created_at is insert-only; updated_at is set by JPA auditing on each change.
ALTER TABLE medical_shifts
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ;