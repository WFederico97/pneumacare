package wfederico.pneumacare.clinical;

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
import wfederico.pneumacare.clinical.application.ClinicalConsultantInsightService;
import wfederico.pneumacare.clinical.web.dto.InsightResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack cache-aside round-trip against Testcontainers Postgres.
 * Disabled by repo convention; run individually with:
 * mvnw.cmd test -Dtest=ClinicalConsultantInsightIntegrationTest
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=ClinicalConsultantInsightIntegrationTest test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ClinicalConsultantInsightIntegrationTest {

    @Autowired
    private ClinicalConsultantInsightService service;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private UUID evaluationId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
    }

    @BeforeEach
    void seed() {
        // Dev profile disables Flyway; Hibernate created the tables empty. Clean own
        // state and seed a reference row plus an evaluation with an UNFAVORABLE band.
        jdbcTemplate.update("DELETE FROM clinical_consultant_insights");
        jdbcTemplate.update("DELETE FROM medical_reference WHERE source_ref LIKE 'IT-%'");
        jdbcTemplate.update("""
                INSERT INTO medical_reference
                    (metric, band, range_descriptor, context, guidance_text, source_ref, priority)
                VALUES
                    ('RSBI', 'UNFAVORABLE', '> 105', 'weaning readiness',
                     'RSBI above 105 predicts weaning failure.', 'IT-RSBI', 70)
                """);

        evaluationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO evaluations
                    (id, patient_id, shift_id, physical_ventilator_id, f, vt, pao2, fio2, pplat, peep,
                     rsbi_snapshot, pafi_snapshot, cstat_snapshot,
                     rsbi_interpretation, pafi_classification, cstat_interpretation,
                     alert_triggered, created_by)
                VALUES
                    (?, ?, ?, ?, 30, 500, 90, 0.30, 25, 5,
                     110.00, 300.00, 80.00,
                     'UNFAVORABLE', 'NORMAL', 'NORMAL',
                     false, ?)
                """,
                evaluationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("first read composes and persists; second read returns the cached row")
    void cacheAsideRoundTrip() {
        InsightResponse first = service.getOrCreate(evaluationId);
        assertThat(first.cached()).isFalse();
        assertThat(first.insightText()).contains("RSBI above 105 predicts weaning failure.");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM clinical_consultant_insights WHERE evaluation_id = ?",
                Integer.class, evaluationId);
        assertThat(rows).isEqualTo(1);

        InsightResponse second = service.getOrCreate(evaluationId);
        assertThat(second.cached()).isTrue();
        assertThat(second.insightText()).isEqualTo(first.insightText());
    }
}
