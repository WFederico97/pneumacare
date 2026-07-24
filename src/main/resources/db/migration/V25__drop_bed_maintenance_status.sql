-- =============================================================================
-- V21__drop_bed_maintenance_status.sql
-- PneumaCare - Remove the MAINTENANCE status from ICU beds. Beds are now only
-- AVAILABLE or OCCUPIED. Any existing maintenance beds are normalized to
-- AVAILABLE before the tightened CHECK constraint is applied.
--
-- Note: this does NOT affect physical_ventilators, which keep their own
-- MAINTENANCE status (equipment maintenance is a separate concept).
-- =============================================================================

UPDATE icu_beds SET status = 'AVAILABLE' WHERE status = 'MAINTENANCE';

ALTER TABLE icu_beds DROP CONSTRAINT IF EXISTS ck_icu_beds_status;

ALTER TABLE icu_beds
    ADD CONSTRAINT ck_icu_beds_status
        CHECK (status IN ('AVAILABLE', 'OCCUPIED'));
