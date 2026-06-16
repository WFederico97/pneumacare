package wfederico.pneumacare.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for SBT recording.
 *
 * <p>Spins up real PostgreSQL via Testcontainers, dev profile (Hibernate creates
 * the schema; Flyway disabled). Seeds a patient + an OPEN shift via the
 * repositories (so PII encryption is applied correctly) and verifies that a valid
 * SBT is persisted with its recorded timestamp, and that a non-positive duration
 * is rejected.
 *
 * <p>Requires Docker. Run: {@code ./mvnw -Dtest=SbtIntegrationTest test}
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=SbtIntegrationTest test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SbtIntegrationTest {

    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IcuRepository icuRepository;
    @Autowired
    private PatientIdentityRepository identityRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired
    private MedicalShiftRepository shiftRepository;
    @Autowired
    private SbtRepository sbtRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private UUID patientId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);

        sbtRepository.deleteAll();
        shiftRepository.deleteAll();
        patientRepository.deleteAll();
        identityRepository.deleteAll();

        IcuJpaEntity icu = icuRepository.findById(IcuTestDataSeeder.ICU_ID).orElseThrow();

        PatientIdentityJpaEntity identity = identityRepository.save(PatientIdentityJpaEntity.builder()
                .firstName("Test")
                .lastName("Patient")
                .birthDate(LocalDate.of(1980, 1, 1))
                .build());

        PatientJpaEntity patient = patientRepository.save(PatientJpaEntity.builder()
                .icu(icu)
                .identity(identity)
                .clinicalStatus(ClinicalStatus.ADMITTED)
                .respiratoryStatus(RespiratoryStatus.SPONTANEOUS)
                .build());
        this.patientId = patient.getId();

        shiftRepository.save(MedicalShiftJpaEntity.builder()
                .icuId(IcuTestDataSeeder.ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build());
    }

    private String body(int duration, String result) {
        return """
                { "patientId": "%s", "durationMinutes": %d, "toleranceResult": "%s" }
                """.formatted(patientId, duration, result);
    }

    @Test
    @DisplayName("valid SBT is persisted with its recorded timestamp")
    void validSbt_isPersisted() throws Exception {
        mockMvc.perform(post("/api/v1/procedures/sbt")
                        .contentType(APPLICATION_JSON).content(body(30, "SUCCESS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.toleranceResult").value("SUCCESS"))
                .andExpect(jsonPath("$.data.recordedAt").isNotEmpty());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spontaneous_breathing_trials WHERE patient_id = ?", Long.class, patientId);
        assertThat(count).isEqualTo(1L);

        // recorded_at is exposed from the audited created_at column.
        String createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM spontaneous_breathing_trials WHERE patient_id = ?", String.class, patientId);
        assertThat(createdAt)
                .as("created_at must be set by auditing (OffsetDateTime provider)")
                .isNotNull();
    }

    @Test
    @DisplayName("non-positive duration is rejected with 422 and writes nothing")
    void zeroDuration_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/procedures/sbt")
                        .contentType(APPLICATION_JSON).content(body(0, "SUCCESS")))
                .andExpect(status().isUnprocessableEntity());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM spontaneous_breathing_trials WHERE patient_id = ?", Long.class, patientId);
        assertThat(count).isEqualTo(0L);
    }
}
