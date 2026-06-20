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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the medical shift lifecycle (PNMC-91).
 *
 * <p>Spins up real PostgreSQL via Testcontainers, dev profile (Hibernate creates
 * the schema; Flyway disabled). Verifies the open→close flow end-to-end and acts
 * as a regression guard for the JPA-auditing {@code created_at} population that
 * broke when {@code EntityBase} switched to {@code OffsetDateTime}.
 *
 * <p>Requires Docker. Run: {@code ./mvnw -Dtest=MedicalShiftIntegrationTest test}
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=MedicalShiftIntegrationTest test")
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
class MedicalShiftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

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
    void cleanShifts() {
        // These tests assume no OPEN shift exists for the seeded ICU. The Testcontainers
        // Postgres is shared with the other RANDOM_PORT integration tests via the cached
        // Spring context, so clear shift state up front to stay isolated from run order.
        jdbcTemplate.update("DELETE FROM shift_handovers");
        jdbcTemplate.update("DELETE FROM medical_shifts");
    }

    private static String openBody() {
        return "{ \"icuId\": \"%s\" }".formatted(IcuTestDataSeeder.ICU_ID);
    }

    @Test
    @DisplayName("open then close round-trip succeeds and persists audit timestamps")
    void openThenClose_roundTrip_succeedsAndAudits() throws Exception {
        // AC1 — open
        MvcResult opened = mockMvc.perform(post("/api/v1/shifts")
                        .contentType(APPLICATION_JSON).content(openBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(opened.getResponse().getContentAsString());
        String shiftId = body.get("data").get("id").asString();

        // Regression guard — created_at must be populated by JPA auditing.
        String createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM medical_shifts WHERE id = ?::uuid", String.class, shiftId);
        assertThat(createdAt)
                .as("created_at must be set by auditing (OffsetDateTime provider)")
                .isNotNull();

        // AC4 — close
        mockMvc.perform(patch("/api/v1/shifts/" + shiftId + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.endTime").isNotEmpty());
    }

    @Test
    @DisplayName("second open for same ICU returns 409 (AC2, app-level guard in dev)")
    void secondOpenSameIcu_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/shifts")
                        .contentType(APPLICATION_JSON).content(openBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/shifts")
                        .contentType(APPLICATION_JSON).content(openBody()))
                .andExpect(status().isConflict());
    }
}
