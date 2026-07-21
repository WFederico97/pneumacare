package wfederico.pneumacare.shared.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shared.security.bootstrap.BootstrapAdminProperties;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a curated demo dataset (6 patients + evaluations in a dedicated Demo ICU)
 * on startup, once, for demonstration builds.
 *
 * <p>Active only when {@code app.demo.seed.enabled=true} (set in {@code .env.prod}),
 * so it never runs in dev/test/CI. Idempotent: a no-op once the Demo ICU exists.
 * Ordered after {@code BootstrapAdminSeeder} (LOWEST_PRECEDENCE) so the
 * {@code admin} user exists to reference as chief/creator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(DemoDataSeeder.ORDER)
@EnableConfigurationProperties(DemoDataProperties.class)
@ConditionalOnProperty(prefix = "app.demo.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final int ORDER = Ordered.LOWEST_PRECEDENCE; // after admin seeder (Task 6)

    /** Deterministic so app.security.default-icu-id can point at it (Task 8). */
    static final String DEMO_ICU_ID = "eeeeeeee-0000-0000-0000-000000000001";
    static final String DEMO_ICU_CODE = "DEMO-ICU";

    private final JdbcClient jdbcClient;
    private final BootstrapAdminProperties adminProperties;

    /** Ids created during seeding, threaded between steps. */
    private record DemoContext(UUID shiftId, UUID ventilatorId, UUID adminUserId, List<UUID> bedIds) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoAlreadySeeded()) {
            log.info("Demo data already present (ICU '{}'); skipping seed.", DEMO_ICU_CODE);
            return;
        }
        log.info("Seeding demo dataset (Demo ICU + 6 patients)...");
        DemoContext ctx = seedInfrastructure();
        // Patients + evaluations added in Tasks 5–6:
        // seedPatientsAndEvaluations(ctx);
        log.info("Demo dataset seeded.");
    }

    private boolean demoAlreadySeeded() {
        Integer count = jdbcClient.sql(
                "SELECT count(*) FROM intensive_care_units WHERE code = :code")
                .param("code", DEMO_ICU_CODE)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private DemoContext seedInfrastructure() {
        UUID adminUserId = jdbcClient.sql(
                "SELECT id FROM users WHERE username = :u")
                .param("u", adminProperties.getUsername())
                .query(UUID.class)
                .single();

        UUID provinceId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO provinces (id, name, region) VALUES (:id, 'Demo', 'Demo')")
                .param("id", provinceId).update();

        UUID hospitalId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO hospitals (id, province_id, name, institutional_type_id)
                VALUES (:id, :prov, 'Hospital Demo', (SELECT MIN(id) FROM institutional_types))
                """)
                .param("id", hospitalId).param("prov", provinceId).update();

        UUID icuId = UUID.fromString(DEMO_ICU_ID);
        jdbcClient.sql("""
                INSERT INTO intensive_care_units (id, hospital_id, name, code)
                VALUES (:id, :hosp, 'Demo ICU', :code)
                """)
                .param("id", icuId).param("hosp", hospitalId).param("code", DEMO_ICU_CODE).update();

        List<UUID> bedIds = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            UUID bedId = UUID.randomUUID();
            bedIds.add(bedId);
            jdbcClient.sql("""
                    INSERT INTO icu_beds (id, icu_id, bed_number, status)
                    VALUES (:id, :icu, :num, 'OCCUPIED')
                    """)
                    .param("id", bedId).param("icu", icuId)
                    .param("num", String.format("DEMO-%02d", i)).update();
        }

        UUID modelId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO ventilator_models (id, brand, model, software_version)
                VALUES (:id, 'TECME', 'Tourus Demo', '1.0')
                """)
                .param("id", modelId).update();

        UUID ventilatorId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO physical_ventilators (id, icu_id, model_id, serial_number, status)
                VALUES (:id, :icu, :model, 'DEMO-VENT-001', 'AVAILABLE')
                """)
                .param("id", ventilatorId).param("icu", icuId).param("model", modelId).update();
        // second ventilator for realism
        jdbcClient.sql("""
                INSERT INTO physical_ventilators (id, icu_id, model_id, serial_number, status)
                VALUES (:id, :icu, :model, 'DEMO-VENT-002', 'AVAILABLE')
                """)
                .param("id", UUID.randomUUID()).param("icu", icuId).param("model", modelId).update();

        UUID shiftId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO medical_shifts (id, icu_id, chief_user_id, start_time, end_time, status)
                VALUES (:id, :icu, :chief, :start, NULL, 'OPEN')
                """)
                .param("id", shiftId).param("icu", icuId).param("chief", adminUserId)
                .param("start", OffsetDateTime.now().minusDays(3)).update();

        return new DemoContext(shiftId, ventilatorId, adminUserId, bedIds);
    }
}
