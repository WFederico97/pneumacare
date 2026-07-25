-- =============================================================================
-- V30__add_decannulation_airway_event.sql
-- Admits DECANNULATION into the airway_events.event_type CHECK constraint.
--
-- TRACHEOSTOMY was a terminal airway state: no event returned the patient to a
-- natural airway, so a tracheostomised patient could never be discharged home
-- or to the ward (the discharge airway guard added in V29 rejects those
-- dispositions while an artificial airway is in place). DECANNULATION closes
-- the cycle: TRACHEOSTOMY -> SPONTANEOUS.
--
-- Runs only in staging/prod (Flyway disabled in dev).
-- =============================================================================

ALTER TABLE airway_events DROP CONSTRAINT ck_airway_events_event_type;

ALTER TABLE airway_events
    ADD CONSTRAINT ck_airway_events_event_type
        CHECK (event_type IN ('INTUBATION', 'EXTUBATION', 'TRACHEOSTOMY', 'DECANNULATION'));
