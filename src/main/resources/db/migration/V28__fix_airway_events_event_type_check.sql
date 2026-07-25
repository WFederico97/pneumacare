-- =============================================================================
-- V28__fix_airway_events_event_type_check.sql
-- Corrects the airway_events.event_type CHECK constraint.
--
-- V6 defined it with Spanish clinical labels
-- ('INTUBACIÓN', 'EXTUBACIÓN', 'REINTUBACIÓN', 'ASPIRACIÓN'), but the
-- AirwayEventType enum is persisted by name in English
-- (INTUBATION / EXTUBATION / TRACHEOSTOMY). The stale constraint rejected every
-- real airway-event insert against a Flyway-migrated database.
--
-- Runs only in staging/prod (Flyway disabled in dev).
-- =============================================================================

ALTER TABLE airway_events DROP CONSTRAINT ck_airway_events_event_type;

ALTER TABLE airway_events
    ADD CONSTRAINT ck_airway_events_event_type
        CHECK (event_type IN ('INTUBATION', 'EXTUBATION', 'TRACHEOSTOMY'));
