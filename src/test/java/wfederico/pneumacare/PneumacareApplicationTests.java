package wfederico.pneumacare;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context-load smoke test.
 *
 * <p>This test requires live infrastructure (PostgreSQL + Redis) and is therefore
 * disabled for local {@code ./mvnw test} runs.
 *
 * <p>In CI the full stack is provided by service containers defined in the
 * GitHub Actions workflow ({@code .github/workflows/ci.yml}). To run locally,
 * start the full stack via {@code docker compose up}, remove this {@code @Disabled}
 * annotation and run:
 * <pre>
 *   ./mvnw -Dtest=PneumacareApplicationTests test
 * </pre>
 */
@SpringBootTest(properties = {
        "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.docker.compose.enabled=false"
})
@Disabled("Requires live PostgreSQL + Redis. Runs in CI via service containers.")
class PneumacareApplicationTests {

    @Test
    void contextLoads() {
    }
}
