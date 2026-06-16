-- =============================================================================
-- V12__sbt_recorded_result_columns.sql
-- PNMC-95 — Spontaneous Breathing Trials (SBT)
--
-- PNMC-95 models an SBT as a recorded result (duration + outcome), not a
-- time-tracked trial. Two adjustments to the V1 spontaneous_breathing_trials
-- table:
--   1. Add audit columns so the entity can extend EntityBase. The ticket's
--      recorded_at is exposed from created_at (append-only: updated_at stays null).
--   2. Drop the NOT NULL on start_time, which this flow does not capture.
--
-- Runs only in staging/prod (Flyway disabled in dev; dev uses ddl-auto:update).
-- =============================================================================

ALTER TABLE spontaneous_breathing_trials
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ,
    ALTER COLUMN start_time DROP NOT NULL;
