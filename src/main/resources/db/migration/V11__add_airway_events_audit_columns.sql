-- =============================================================================
-- V11__add_airway_events_audit_columns.sql
-- PNMC-94 — Airway Events Management
--
-- Adds row-level audit timestamps to airway_events so it can extend EntityBase.
-- airway_events is append-only: created_at is set at insert, updated_at stays
-- null. created_at (row persisted) is distinct from event_time (clinical time).
--
-- Runs only in staging/prod (Flyway disabled in dev).
-- =============================================================================

ALTER TABLE airway_events
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ;