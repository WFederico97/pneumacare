-- =============================================================================
-- V7__add_evaluation_interpretations.sql
-- PneumaCare — Persist clinical interpretation enums on evaluations
--
-- Motivation
-- ----------
-- The three respiratory indices (RSBI, PaFi, Cstat) were stored as numeric
-- snapshots, but their clinical interpretations were only computed server-side
-- and returned in the API response — never persisted. They were re-derivable
-- from the snapshots, but only against the *current* classification thresholds.
--
-- Those thresholds are clinical constants that may be revised over time
-- (e.g. RsbiInterpretation, PafiClassification, CstatInterpretation). Re-deriving
-- an old evaluation against new thresholds would silently change the recorded
-- judgement. Persisting the interpretation captured at evaluation time makes the
-- record authoritative and audit-stable.
--
-- Stored as VARCHAR via @Enumerated(EnumType.STRING). CHECK constraints mirror
-- the Java enum values (defence-in-depth, consistent with V6 status columns).
-- Columns are nullable to match the snapshot columns they accompany.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. evaluations — interpretation columns
-- ---------------------------------------------------------------------------

ALTER TABLE evaluations
    ADD COLUMN rsbi_interpretation  VARCHAR(20),
    ADD COLUMN pafi_classification  VARCHAR(20),
    ADD COLUMN cstat_interpretation VARCHAR(20);

COMMENT ON COLUMN evaluations.rsbi_interpretation  IS
    'RSBI weaning-outcome interpretation captured at evaluation time (RsbiInterpretation enum).';
COMMENT ON COLUMN evaluations.pafi_classification  IS
    'PaFi Berlin-Definition ARDS classification captured at evaluation time (PafiClassification enum).';
COMMENT ON COLUMN evaluations.cstat_interpretation IS
    'Static-compliance interpretation captured at evaluation time (CstatInterpretation enum).';

-- ---------------------------------------------------------------------------
-- 2. CHECK constraints — mirror the Java enum vocabularies
-- ---------------------------------------------------------------------------

ALTER TABLE evaluations
    ADD CONSTRAINT ck_evaluations_rsbi_interpretation
        CHECK (rsbi_interpretation IN ('FAVORABLE', 'BORDERLINE', 'UNFAVORABLE')),
    ADD CONSTRAINT ck_evaluations_pafi_classification
        CHECK (pafi_classification IN ('NORMAL', 'AT_RISK', 'MILD_ARDS', 'MODERATE_ARDS', 'SEVERE_ARDS')),
    ADD CONSTRAINT ck_evaluations_cstat_interpretation
        CHECK (cstat_interpretation IN ('HIGH', 'NORMAL', 'LOW'));

-- ---------------------------------------------------------------------------
-- 3. evaluations_aud — keep the Envers shadow table column-complete
-- ---------------------------------------------------------------------------

ALTER TABLE evaluations_aud
    ADD COLUMN rsbi_interpretation  VARCHAR(20),
    ADD COLUMN pafi_classification  VARCHAR(20),
    ADD COLUMN cstat_interpretation VARCHAR(20);
