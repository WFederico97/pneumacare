-- =============================================================================
-- V20__create_clinical_consultant_insights.sql
-- PneumaCare - Cache-aside store for composed clinical consultant guidance.
--
-- One row per evaluation. The first read composes the guidance via the
-- DB-backed consultant and persists it here; subsequent reads return this copy.
-- Non-PII: insight_text is derived solely from curated medical_reference rows.
-- =============================================================================

CREATE TABLE clinical_consultant_insights (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    evaluation_id UUID        NOT NULL,
    insight_text  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT pk_clinical_consultant_insights PRIMARY KEY (id),
    CONSTRAINT uq_cci_evaluation_id            UNIQUE (evaluation_id),
    CONSTRAINT fk_cci_evaluation               FOREIGN KEY (evaluation_id)
        REFERENCES evaluations (id)
);

COMMENT ON TABLE clinical_consultant_insights IS
    'Cache-aside store of composed clinical consultant guidance, one row per evaluation.';
