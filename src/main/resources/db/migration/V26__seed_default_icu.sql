-- =============================================================================
-- V26__seed_default_icu.sql
-- Seed a single default ICU (+ its province, hospital and three beds) so the
-- dashboard bed grid works out of the box in staging/prod.
--
-- In dev this data is inserted at startup by IcuTestDataSeeder (@Profile("dev"));
-- Flyway is disabled there. In staging/prod the seeder never runs, leaving these
-- tables empty. The self-issued JWT carries icu_id = the ICU below (see
-- JwtService / app.security.default-icu-id), so beds must exist for it.
--
-- Deterministic UUIDs match IcuTestDataSeeder so dev and prod share one ICU id.
-- All inserts are idempotent (ON CONFLICT DO NOTHING).
-- =============================================================================

INSERT INTO provinces (id, name, region)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'Buenos Aires', 'Centro')
ON CONFLICT (id) DO NOTHING;

INSERT INTO hospitals (id, province_id, name, institutional_type_id)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001',
        'aaaaaaaa-0000-0000-0000-000000000001',
        'Hospital General de Agudos',
        (SELECT MIN(id) FROM institutional_types))
ON CONFLICT (id) DO NOTHING;

INSERT INTO intensive_care_units (id, hospital_id, name, code)
VALUES ('cccccccc-0000-0000-0000-000000000001',
        'bbbbbbbb-0000-0000-0000-000000000001',
        'UTI Central', 'UTI-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO icu_beds (id, icu_id, bed_number, status) VALUES
    ('dddddddd-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 'BED-001', 'AVAILABLE'),
    ('dddddddd-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000001', 'BED-002', 'AVAILABLE'),
    ('dddddddd-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000001', 'BED-003', 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;
