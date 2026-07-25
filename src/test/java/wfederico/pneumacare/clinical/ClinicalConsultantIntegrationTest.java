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
import wfederico.pneumacare.clinical.application.ClinicalConsultantService;
import wfederico.pneumacare.clinical.domain.ConsultantGuidance;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack consultant composition against Testcontainers Postgres.
 * Disabled by repo convention; run individually with:
 * mvnw.cmd test -Dtest=ClinicalConsultantIntegrationTest
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=ClinicalConsultantIntegrationTest test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ClinicalConsultantIntegrationTest {

    @Autowired
    private ClinicalConsultantService service;
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
    void seedReference() {
        // Dev profile disables Flyway (Hibernate creates the table, empty); seed
        // the row this test needs and clear any left by a previous run.
        jdbcTemplate.update("DELETE FROM medical_reference WHERE source_ref LIKE 'IT-%'");
        jdbcTemplate.update("""
                INSERT INTO medical_reference
                    (metric, band, range_descriptor, context, guidance_text, source_ref, priority)
                VALUES
                    ('RSBI', 'UNFAVORABLE', '> 105', 'weaning readiness',
                     'RSBI above 105 predicts weaning failure.', 'IT-RSBI', 70)
                """);
    }

    @Test
    @DisplayName("composes guidance from the seeded RSBI/UNFAVORABLE reference row")
    void composesFromSeededRow() {
        VentilatorEvaluationResult result = new VentilatorEvaluationResult(
                new RsbiResult(110.0, RsbiInterpretation.UNFAVORABLE),
                new PafiResult(420.0, PafiClassification.NORMAL),
                new CstatResult(80.0, CstatInterpretation.NORMAL));

        ConsultantGuidance guidance = service.compose(result);

        assertThat(guidance.text()).contains("RSBI above 105 predicts weaning failure.");
        assertThat(guidance.sources()).contains("IT-RSBI");
    }

    @Test
    @DisplayName("all-normal metrics return the safe default")
    void allNormalReturnsSafeDefault() {
        VentilatorEvaluationResult result = new VentilatorEvaluationResult(
                new RsbiResult(60.0, RsbiInterpretation.FAVORABLE),
                new PafiResult(420.0, PafiClassification.NORMAL),
                new CstatResult(80.0, CstatInterpretation.NORMAL));

        ConsultantGuidance guidance = service.compose(result);

        assertThat(guidance.text()).isEqualTo("Sin datos de referencia suficientes para una recomendación.");
    }
}
