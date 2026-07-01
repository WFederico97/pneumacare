-- =============================================================================
-- V16__create_clinical_alerts_log.sql
-- PNMC-100 — Clinical Alerts Persistence Log
--
-- Audit trail for patient-risk alert dispatch. One row per PatientRiskEvent,
-- keyed by event_id. Written PENDING before the n8n webhook call and updated to
-- DELIVERED / FAILED after. In dev (ddl=update, Flyway disabled) Hibernate creates
-- this table automatically from ClinicalAlertLogJpaEntity; this script keeps
-- staging/prod (ddl=validate) in sync.
-- =============================================================================

CREATE TABLE clinical_alerts_log (
    id         UUID         NOT NULL,
    event_id   UUID         NOT NULL,
    payload    JSONB        NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT pk_clinical_alerts_log PRIMARY KEY (id),
    CONSTRAINT uq_clinical_alerts_log_event_id UNIQUE (event_id),
    CONSTRAINT ck_clinical_alerts_log_status
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED'))
);
