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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoAlreadySeeded()) {
            log.info("Demo data already present (ICU '{}'); skipping seed.", DEMO_ICU_CODE);
            return;
        }
        log.info("Seeding demo dataset (Demo ICU + 6 patients)...");
        // Seeding steps added in Tasks 4–6.
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
}
