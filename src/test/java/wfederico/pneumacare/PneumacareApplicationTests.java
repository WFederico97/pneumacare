package wfederico.pneumacare;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Context-load smoke test.
 *
 * <p>Verifies that the full Spring application context starts cleanly.
 * PostgreSQL is provided by Testcontainers ({@link TestcontainersConfiguration});
 * Redis is mocked to avoid requiring a live Redis instance.
 *
 * <p>Disabled by default so that {@code ./mvnw test} can run without Docker.
 * To run locally, ensure Docker is running and remove (or comment out) the
 * {@code @Disabled} annotation:
 * <pre>
 *   ./mvnw -Dtest=PneumacareApplicationTests test
 * </pre>
 *
 * <p>In GitHub Actions the context-load check is performed by the
 * {@code ci.yml} pipeline using service containers rather than Testcontainers,
 * so this test intentionally stays disabled there.
 */
@SpringBootTest(properties = {
        "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.docker.compose.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@Disabled("Requires Docker. Remove @Disabled and run: ./mvnw -Dtest=PneumacareApplicationTests test")
class PneumacareApplicationTests {

    /** Mocked to satisfy the rate-limiting {@code SecurityFilter} without a live Redis. */
    @MockitoBean
    @SuppressWarnings("unused")
    private StringRedisTemplate redisTemplate;

    @Test
    void contextLoads() {
    }
}
