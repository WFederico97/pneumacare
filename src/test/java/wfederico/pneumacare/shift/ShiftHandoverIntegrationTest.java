package wfederico.pneumacare.shift;

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
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverRepository;

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
 * Full-stack integration tests for shift handover notes.
 *
 * <p>Spins up real PostgreSQL via Testcontainers, dev profile (Hibernate creates
 * the schema; Flyway disabled). Seeds shifts via the repository and verifies that a
 * note persists on an OPEN shift (with its created_at), that multiple notes are
 * allowed per shift, and that a CLOSED shift is rejected.
 *
 * <p>Requires Docker. Run: {@code ./mvnw -Dtest=ShiftHandoverIntegrationTest test}
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=ShiftHandoverIntegrationTest test")
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
class ShiftHandoverIntegrationTest {

    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MedicalShiftRepository shiftRepository;
    @Autowired
    private ShiftHandoverRepository handoverRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private UUID openShiftId;
    private UUID closedShiftId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);

        handoverRepository.deleteAll();
        shiftRepository.deleteAll();

        MedicalShiftJpaEntity open = shiftRepository.save(MedicalShiftJpaEntity.builder()
                .icuId(IcuTestDataSeeder.ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build());
        this.openShiftId = open.getId();

        MedicalShiftJpaEntity closed = shiftRepository.save(MedicalShiftJpaEntity.builder()
                .icuId(IcuTestDataSeeder.ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).minusHours(12))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.CLOSED)
                .build());
        this.closedShiftId = closed.getId();
    }

    private String body(String content) {
        return "{ \"notesContent\": \"" + content + "\" }";
    }

    @Test
    @DisplayName("note on an OPEN shift is persisted with its created_at, and many are allowed")
    void note_onOpenShift_persistedAndMultipleAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/shifts/" + openShiftId + "/handovers")
                        .contentType(APPLICATION_JSON).content(body("Primera nota")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.notesContent").value("Primera nota"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        // A second note on the same shift must be allowed (V13 dropped UNIQUE(shift_id)).
        mockMvc.perform(post("/api/v1/shifts/" + openShiftId + "/handovers")
                        .contentType(APPLICATION_JSON).content(body("Segunda nota")))
                .andExpect(status().isCreated());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shift_handovers WHERE shift_id = ?", Long.class, openShiftId);
        assertThat(count).isEqualTo(2L);

        String createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM shift_handovers WHERE shift_id = ? LIMIT 1", String.class, openShiftId);
        assertThat(createdAt)
                .as("created_at must be set by auditing (OffsetDateTime provider)")
                .isNotNull();
    }

    @Test
    @DisplayName("note on a CLOSED shift is rejected with 409 and writes nothing")
    void note_onClosedShift_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/shifts/" + closedShiftId + "/handovers")
                        .contentType(APPLICATION_JSON).content(body("nota")))
                .andExpect(status().isConflict());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shift_handovers WHERE shift_id = ?", Long.class, closedShiftId);
        assertThat(count).isEqualTo(0L);
    }
}
