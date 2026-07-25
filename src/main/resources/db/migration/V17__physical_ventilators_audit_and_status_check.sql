-- physical_ventilators exists since V1 but lacks EntityBase audit columns.
-- staging/prod run ddl-auto=validate, so the columns must exist before the
-- inventory JPA entity ships.
ALTER TABLE physical_ventilators
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ;

ALTER TABLE physical_ventilators
    ADD CONSTRAINT ck_physical_ventilators_status
        CHECK (status IN ('AVAILABLE', 'IN_USE', 'MAINTENANCE'));
