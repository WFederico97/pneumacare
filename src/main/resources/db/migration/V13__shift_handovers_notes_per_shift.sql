-- =============================================================================
-- V13__shift_handovers_notes_per_shift.sql
-- PNMC-92 — Shift Handover Notes
--
-- The V1 shift_handovers table modelled a single structured handover per shift
-- (incoming/outgoing/critical summary, UNIQUE(shift_id)). PNMC-92 models simple,
-- immutable, multiple-per-shift notes, so:
--   1. Drop the one-per-shift unique constraint.
--   2. Add author_id, notes_content and audit columns (recorded_at = created_at).
--   3. Add a plain index on shift_id for the history query (previously provided by
--      the unique constraint).
-- The legacy columns (incoming_notes/outgoing_notes/critical_events_summary/
-- closed_at) are left in place but unused by this feature.
--
-- author_id/notes_content are added NOT NULL: shift_handovers has never been
-- written to (no prior API), so the table is empty. Runs only in staging/prod.
-- =============================================================================

ALTER TABLE shift_handovers
    DROP CONSTRAINT uq_shift_handovers_shift,
    ADD COLUMN author_id     UUID        NOT NULL,
    ADD COLUMN notes_content TEXT        NOT NULL,
    ADD COLUMN created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at    TIMESTAMPTZ;

CREATE INDEX idx_shift_handovers_shift ON shift_handovers (shift_id);
