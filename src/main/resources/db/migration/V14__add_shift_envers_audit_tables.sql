-- =============================================================================
-- V14__add_shift_envers_audit_tables.sql
-- PNMC-134 — Observability & Envers Audit Trail for Shifts
--
-- Creates the Hibernate Envers schema for the audited shift entities so that
-- staging/prod (Flyway + ddl=validate) match what Hibernate expects. In dev
-- (ddl=update, Flyway disabled) Hibernate creates these tables automatically.
--
-- Tables created:
--   * revinfo             — custom revision entity (ShiftRevisionEntity); the
--                           actor_id column records the acting user per revision.
--   * medical_shifts_aud  — revision history for medical_shifts.
--   * shift_handovers_aud — revision history for shift_handovers.
-- Column shapes mirror the audited base-table columns; Envers adds rev/revtype
-- and makes the audited columns nullable. Existing rows are not back-filled —
-- audit history starts at the first write after this migration is applied.
-- =============================================================================

-- Revision metadata table + its sequence (allocationSize = 1).
CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE revinfo (
    rev      INTEGER NOT NULL,
    revtstmp BIGINT,
    actor_id UUID,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

-- Revision history for medical_shifts.
CREATE TABLE medical_shifts_aud (
    rev           INTEGER     NOT NULL,
    revtype       SMALLINT,
    id            UUID        NOT NULL,
    icu_id        UUID,
    chief_user_id UUID,
    start_time    TIMESTAMPTZ,
    end_time      TIMESTAMPTZ,
    status        VARCHAR(20),
    CONSTRAINT pk_medical_shifts_aud  PRIMARY KEY (rev, id),
    CONSTRAINT fk_medical_shifts_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

-- Revision history for shift_handovers.
CREATE TABLE shift_handovers_aud (
    rev           INTEGER NOT NULL,
    revtype       SMALLINT,
    id            UUID    NOT NULL,
    shift_id      UUID,
    author_id     UUID,
    notes_content TEXT,
    CONSTRAINT pk_shift_handovers_aud  PRIMARY KEY (rev, id),
    CONSTRAINT fk_shift_handovers_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);
