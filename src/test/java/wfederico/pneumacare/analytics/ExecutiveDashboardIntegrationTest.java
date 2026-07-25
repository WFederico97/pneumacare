package wfederico.pneumacare.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.analytics.application.ExecutiveAnalyticsService;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the executive dashboard aggregation against Testcontainers Postgres.
 * Disabled by repo convention; run individually with:
 * mvnw.cmd test -Dtest=ExecutiveDashboardIntegrationTest
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=ExecutiveDashboardIntegrationTest test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ExecutiveDashboardIntegrationTest {

    @Autowired
    private ExecutiveAnalyticsService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
    }

    @BeforeEach
    void seedAlerts() {
        // Clean own state, then log one alert inside the 7-day window and one outside it.
        jdbcTemplate.update("DELETE FROM clinical_alerts_log");
        jdbcTemplate.update("""
                INSERT INTO clinical_alerts_log (id, event_id, payload, status, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), '{}'::jsonb, 'DELIVERED', now())
                """);
        jdbcTemplate.update("""
                INSERT INTO clinical_alerts_log (id, event_id, payload, status, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), '{}'::jsonb, 'DELIVERED', now() - interval '10 days')
                """);
    }

    @Test
    @DisplayName("alert frequency counts only alerts within the last 7 days")
    void alertFrequencyWithinWindow() {
        ExecutiveDashboardResponse response = service.dashboard();

        assertThat(response).isNotNull();
        assertThat(response.alertFrequencyLast7Days()).isEqualTo(1L);
        assertThat(response.occupancyRatePercent()).isGreaterThanOrEqualTo(0.0);
        assertThat(response.equipmentInMaintenanceCount()).isGreaterThanOrEqualTo(0L);
    }
}
