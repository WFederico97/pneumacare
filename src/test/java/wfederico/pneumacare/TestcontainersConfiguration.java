package wfederico.pneumacare;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers configuration for integration tests that require real infrastructure.
 *
 * <p>Provides a Postgres container wired via {@link ServiceConnection} — Spring Boot
 * automatically overrides {@code spring.datasource.*} properties so the application
 * context connects to the container instead of the configured host.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @SpringBootTest
 * @Import(TestcontainersConfiguration.class)
 * class MyIntegrationTest { ... }
 * }</pre>
 *
 * <h2>Redis</h2>
 * A Redis container is not provided here. Integration tests that depend on
 * {@code StringRedisTemplate} (e.g. the rate-limiting {@code SecurityFilter})
 * should add {@code @MockitoBean StringRedisTemplate redisTemplate} and stub
 * {@code redisTemplate.opsForValue().increment(any())} to return {@code 1L}.
 * This avoids pulling in a Redis container dependency for tests that do not
 * exercise caching or rate-limiting behaviour.
 *
 * <h2>Docker</h2>
 * Running tests with this configuration requires Docker.
 * Start with: {@code docker compose up -d} or ensure Docker Desktop is running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * PostgreSQL container. The {@link ServiceConnection} annotation instructs
     * Spring Boot to override the datasource connection properties automatically.
     * Flyway is disabled in the {@code dev} profile; Hibernate manages DDL via
     * {@code ddl-auto: update}.
     *
     * @return the running container (lifecycle managed by Spring Boot test context)
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
