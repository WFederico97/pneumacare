package wfederico.pneumacare.shared.demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import wfederico.pneumacare.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the demo seeder against the real (Flyway-built) prod-like schema.
 *
 * <p>Uses the {@code staging} profile so Flyway runs (full schema + reference
 * seeds) and the dev-only {@code IcuTestDataSeeder} stays off — mirroring
 * {@code LoginFlowIT}. The seeder runs once at context startup; the test asserts
 * the resulting counts and that a second invocation is a no-op.
 *
 * <p>Disabled by house convention; run individually:
 *   mvnw.cmd test -Dtest=DemoDataSeederIT   (Docker required)
 */
@Disabled("Integration test — run individually with -Dtest=DemoDataSeederIT (Docker required)")
@SpringBootTest(properties = {
        "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.jwt.secret=0123456789abcdef0123456789abcdef",
        "app.demo.seed.enabled=true",
        "app.security.bootstrap-admin.enabled=true",
        "app.security.bootstrap-admin.initial-password=DemoPass123!",
        "spring.docker.compose.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("staging")
class DemoDataSeederIT {

    @Autowired JdbcClient jdbcClient;
    @Autowired DemoDataSeeder seeder;

    @Test
    void seedsSixPatientsWithEighteenEvaluationsAndIsIdempotent() {
        // Seeder already ran once on context startup.
        Integer icus = jdbcClient.sql(
                "SELECT count(*) FROM intensive_care_units WHERE code = 'DEMO-ICU'")
                .query(Integer.class).single();
        assertThat(icus).isEqualTo(1);

        Integer patients = jdbcClient.sql("""
                SELECT count(*) FROM patients p
                JOIN intensive_care_units i ON i.id = p.icu_id
                WHERE i.code = 'DEMO-ICU'
                """).query(Integer.class).single();
        assertThat(patients).isEqualTo(6);

        Integer evaluations = jdbcClient.sql("""
                SELECT count(*) FROM evaluations e
                JOIN patients p ON p.id = e.patient_id
                JOIN intensive_care_units i ON i.id = p.icu_id
                WHERE i.code = 'DEMO-ICU'
                """).query(Integer.class).single();
        assertThat(evaluations).isEqualTo(18);

        // Running again must be a no-op (idempotency guard).
        seeder.run(null);
        Integer patientsAfter = jdbcClient.sql("""
                SELECT count(*) FROM patients p
                JOIN intensive_care_units i ON i.id = p.icu_id
                WHERE i.code = 'DEMO-ICU'
                """).query(Integer.class).single();
        assertThat(patientsAfter).isEqualTo(6);
    }
}
