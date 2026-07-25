package wfederico.pneumacare.patient.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds minimal ICU infrastructure data on startup in the {@code dev} profile.
 *
 * <p>Inserts one province, one hospital, one ICU, three beds, and the standard
 * patient identifier types so that the admission API works immediately after
 * first boot without any manual setup.
 *
 * <p>The {@link #run} method is <strong>idempotent</strong>: if any ICU record
 * already exists the seeder skips all inserts and logs a debug message.
 *
 * <h2>Why JdbcTemplate instead of JPA repositories?</h2>
 * Hibernate 7 changed {@code em.merge()} behaviour: calling {@code save()} on
 * an entity with a pre-set UUID that does not yet exist in the database now
 * throws {@code StaleObjectStateException} instead of falling back to INSERT.
 * Using raw {@code INSERT … ON CONFLICT DO NOTHING} via {@link JdbcTemplate}
 * is simpler, faster, and avoids this Hibernate 7 regression entirely.
 *
 * <h2>Province and hospital</h2>
 * Those entities belong to a different bounded context that has not yet been
 * modelled as JPA entities. They are inserted via a {@link JdbcTemplate} native
 * INSERT using deterministic, hard-coded UUIDs so the seeder remains idempotent
 * across restarts and matches the FK chain required by {@code hospitals → provinces}
 * and {@code intensive_care_units → hospitals}.
 *
 * <h2>Identifier types</h2>
 * Flyway migration V4 seeds {@code patient_identifier_types} in staging/prod.
 * In the {@code dev} profile Flyway is disabled, so this seeder inserts the same
 * types here via {@code ON CONFLICT DO NOTHING}.
 *
 * <h2>Bed layout</h2>
 * Three beds are created for the seeded ICU:
 * <ul>
 *   <li>{@code BED-001} — {@code AVAILABLE} (happy-path admission test target)</li>
 *   <li>{@code BED-002} — {@code AVAILABLE} (secondary available bed)</li>
 *   <li>{@code BED-003} — {@code AVAILABLE}</li>
 * </ul>
 */
@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
public class IcuTestDataSeeder implements ApplicationRunner {

    // Deterministic UUIDs — hard-coded so test fixtures can reference them without a DB lookup.
    public static final UUID PROVINCE_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    public static final UUID HOSPITAL_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    public static final UUID ICU_ID      = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    public static final UUID BED_001_ID  = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    public static final UUID BED_002_ID  = UUID.fromString("dddddddd-0000-0000-0000-000000000002");
    public static final UUID BED_003_ID  = UUID.fromString("dddddddd-0000-0000-0000-000000000003");

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long icuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM intensive_care_units", Long.class);
        if (icuCount != null && icuCount > 0) {
            log.debug("IcuTestDataSeeder: ICU data already present, skipping.");
            return;
        }

        log.info("IcuTestDataSeeder: seeding province, hospital, ICU, beds and identifier types.");

        ensureAuxiliaryTables();
        seedProvince();
        seedHospital();
        seedIcu();
        seedBeds();
        seedIdentifierTypes();

        log.info("IcuTestDataSeeder: seeded 1 ICU (id={}) + 3 beds + identifier types.", ICU_ID);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Creates the {@code provinces}, {@code institutional_types}, and
     * {@code hospitals} tables if they do not already exist.
     *
     * <p>These tables belong to a bounded context that has not yet been modelled
     * as JPA entities, so Hibernate's {@code ddl-auto: update} does not create
     * them automatically in the {@code dev} profile. This method makes the seeder
     * fully self-contained — no manual DDL or Flyway run is required to start
     * the application locally.
     *
     * <p>The DDL mirrors the definitions in {@code V1__init_schema.sql} and
     * {@code V5__add_institutional_types.sql} exactly, without indexes
     * (not needed for dev).
     *
     * <p><strong>Note for developers with an existing dev volume:</strong>
     * {@code CREATE TABLE IF NOT EXISTS} will not alter a table that already
     * exists with a different schema. If you previously ran the app with the
     * old {@code institutional_type VARCHAR(50)} column, drop and recreate
     * the Docker volume ({@code docker compose down -v}) to pick up the new DDL.
     */
    private void ensureAuxiliaryTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS provinces (
                    id     UUID         NOT NULL DEFAULT gen_random_uuid(),
                    name   VARCHAR(100) NOT NULL,
                    region VARCHAR(50),
                    CONSTRAINT pk_provinces PRIMARY KEY (id)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS institutional_types (
                    id   SERIAL      NOT NULL,
                    name VARCHAR(50) NOT NULL,
                    CONSTRAINT pk_institutional_types PRIMARY KEY (id),
                    CONSTRAINT uq_institutional_types UNIQUE (name)
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS hospitals (
                    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
                    province_id           UUID         NOT NULL,
                    name                  VARCHAR(150) NOT NULL,
                    institutional_type_id INT,
                    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    CONSTRAINT pk_hospitals              PRIMARY KEY (id),
                    CONSTRAINT fk_hospitals_province     FOREIGN KEY (province_id)
                        REFERENCES provinces (id),
                    CONSTRAINT fk_hospitals_inst_type    FOREIGN KEY (institutional_type_id)
                        REFERENCES institutional_types (id)
                )
                """);

        log.debug("IcuTestDataSeeder: auxiliary tables verified (provinces, institutional_types, hospitals).");
    }

    private void seedProvince() {
        jdbcTemplate.update(
                "INSERT INTO provinces (id, name, region) VALUES (?, ?, ?)" +
                " ON CONFLICT (id) DO NOTHING",
                PROVINCE_ID, "Buenos Aires", "Centro");
    }

    private void seedHospital() {
        // Ensure the type exists (idempotent) then resolve its generated id.
        jdbcTemplate.update(
                "INSERT INTO institutional_types (name) VALUES (?) ON CONFLICT (name) DO NOTHING",
                "PÚBLICO");
        Integer typeId = jdbcTemplate.queryForObject(
                "SELECT id FROM institutional_types WHERE name = ?",
                Integer.class, "PÚBLICO");

        jdbcTemplate.update(
                "INSERT INTO hospitals (id, province_id, name, institutional_type_id)" +
                " VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                HOSPITAL_ID, PROVINCE_ID, "Hospital General de Agudos", typeId);
    }

    /**
     * Inserts the seeded ICU row using a plain SQL INSERT.
     *
     * <p>Using JdbcTemplate instead of {@code IcuRepository.save()} avoids the
     * Hibernate 7 {@code merge()} regression where saving a detached entity with
     * a pre-set UUID that does not yet exist throws
     * {@code StaleObjectStateException}.
     */
    private void seedIcu() {
        jdbcTemplate.update(
                "INSERT INTO intensive_care_units (id, hospital_id, name, code)" +
                " VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                ICU_ID, HOSPITAL_ID, "UTI Central", "UTI-01");
    }

    /**
     * Inserts the three seeded beds using plain SQL INSERTs.
     *
     * <p>Same rationale as {@link #seedIcu()}: avoids Hibernate 7
     * {@code merge()} regression on pre-set UUID entities.
     */
    private void seedBeds() {
        String sql = "INSERT INTO icu_beds (id, icu_id, bed_number, status)" +
                     " VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        jdbcTemplate.update(sql, BED_001_ID, ICU_ID, "BED-001", "AVAILABLE");
        jdbcTemplate.update(sql, BED_002_ID, ICU_ID, "BED-002", "AVAILABLE");
        jdbcTemplate.update(sql, BED_003_ID, ICU_ID, "BED-003", "AVAILABLE");
    }

    /**
     * Seeds the standard Argentine patient identifier types.
     *
     * <p>In staging/prod this is handled by Flyway migration V4.  In the
     * {@code dev} profile Flyway is disabled, so the seeder inserts the same
     * rows here. All inserts use {@code ON CONFLICT DO NOTHING} so the method is
     * safe to call on a database that was previously migrated.
     */
    private void seedIdentifierTypes() {
        // Use INSERT … SELECT … WHERE NOT EXISTS instead of ON CONFLICT so that
        // this works even on an existing dev volume that lacks the UNIQUE constraint
        // (Hibernate ddl-auto:update will add it on first startup, but the seeder
        // runs in the same boot cycle and the constraint may not be present yet).
        String sql = """
                INSERT INTO patient_identifier_types
                    (patient_identifier_type_name, patient_identifier_type_description)
                SELECT ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM patient_identifier_types
                     WHERE patient_identifier_type_name = ?
                )
                """;
        jdbcTemplate.update(sql, "DNI",       "Documento Nacional de Identidad",             "DNI");
        jdbcTemplate.update(sql, "CUIL",      "Código Único de Identificación Laboral",      "CUIL");
        jdbcTemplate.update(sql, "CUIT",      "Código Único de Identificación Tributaria",   "CUIT");
        jdbcTemplate.update(sql, "LE",        "Libreta de Enrolamiento",                     "LE");
        jdbcTemplate.update(sql, "LC",        "Libreta Cívica",                              "LC");
        jdbcTemplate.update(sql, "Pasaporte", "Pasaporte",                                   "Pasaporte");
    }
}
