package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for the patient admission endpoint.
 *
 * <p>These tests spin up a real PostgreSQL database via Testcontainers, run the
 * full Spring Boot application context (dev profile — Flyway disabled, OAuth2
 * excluded, {@code permitAll()} on {@code /api/**}), and verify:
 *
 * <ol>
 *   <li><strong>BDD Scenario 1</strong> — Happy path admission returns 201 with
 *       {@code patientId}, {@code bedId}, and {@code admissionDate}.</li>
 *   <li><strong>BDD Scenario 2</strong> — Missing identifier object returns 400
 *       with a field-level error.</li>
 *   <li><strong>BDD Scenario 3</strong> — AES-256-GCM encryption at rest:
 *       the {@code first_name} column in {@code patient_identities} must NOT
 *       contain plain text after a successful admission.</li>
 * </ol>
 *
 * <h2>Requirements</h2>
 * Docker must be running. Start the Docker daemon (or Docker Desktop) before
 * executing these tests. To run:
 * <pre>
 *   ./mvnw -Dtest=PatientAdmissionIntegrationTest test
 * </pre>
 * Remove the {@code @Disabled} annotation or set environment variable
 * {@code RUN_INTEGRATION_TESTS=true} and reactivate the annotation guard.
 *
 * <h2>Redis</h2>
 * {@link StringRedisTemplate} is mocked to satisfy the rate-limiting
 * {@code SecurityFilter} without needing a live Redis instance.
 * The AES key is a deterministic all-zero Base64 string valid for tests.
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=PatientAdmissionIntegrationTest test")
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
class PatientAdmissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Mocked to avoid requiring a live Redis instance. */
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * Valid admission JSON using the deterministic ICU and bed UUIDs seeded by
     * {@link IcuTestDataSeeder} (which runs automatically in the {@code dev} profile).
     * Identifier type 1 = DNI (seeded by {@code IdentifierTypeDataSeeder}).
     */
    private static String validAdmissionJson() {
        return """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifier": { "identifierTypeId": 1, "value": "35123456" },
                  "icuId": "%s",
                  "bedId": "%s"
                }
                """.formatted(IcuTestDataSeeder.ICU_ID, IcuTestDataSeeder.BED_001_ID);
    }

    // ── BDD Scenario 1 — Happy path ───────────────────────────────────────────

    @Test
    @DisplayName("BDD Scenario 1 — valid admission returns 201 with patientId, bedId, admissionDate")
    void admitPatient_validRequest_returns201WithExpectedFields() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdmissionJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.patientId").isNotEmpty())
                .andExpect(jsonPath("$.data.bedId").value(IcuTestDataSeeder.BED_001_ID.toString()))
                .andExpect(jsonPath("$.data.admissionDate").isNotEmpty())
                .andExpect(jsonPath("$.data.clinicalStatus").value("ADMITTED"))
                .andExpect(jsonPath("$.data.identifier.typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifier.value").value("35123456"))
                .andExpect(jsonPath("$.data.firstName").value("Juan"));
    }

    // ── BDD Scenario 2 — Missing identifier ──────────────────────────────────

    @Test
    @DisplayName("BDD Scenario 2 — missing identifier object returns 400 with field error")
    void admitPatient_missingIdentifier_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "icuId": "%s",
                  "bedId": "%s"
                }
                """.formatted(IcuTestDataSeeder.ICU_ID, IcuTestDataSeeder.BED_001_ID);

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.identifier").exists());
    }

    // ── BDD Scenario 3 — AES ciphertext at rest ───────────────────────────────

    @Test
    @DisplayName("BDD Scenario 3 — first_name column stores AES ciphertext, not plain text")
    void admitPatient_firstNameStoredEncrypted_notPlaintext() throws Exception {
        // Admit the patient (uses BED_002 to avoid conflict with Scenario 1 if run sequentially)
        String body = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifier": { "identifierTypeId": 1, "value": "35123457" },
                  "icuId": "%s",
                  "bedId": "%s"
                }
                """.formatted(IcuTestDataSeeder.ICU_ID, IcuTestDataSeeder.BED_002_ID);

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Query the raw stored value directly from the database
        String storedFirstName = jdbcTemplate.queryForObject(
                "SELECT first_name FROM patient_identities ORDER BY id DESC LIMIT 1",
                String.class);

        // AES-256-GCM with a random 12-byte IV produces Base64-encoded ciphertext.
        // The stored value must never be equal to the plain-text input.
        assertThat(storedFirstName)
                .as("first_name must be stored as AES-256-GCM ciphertext, not plain text")
                .isNotNull()
                .isNotEqualTo("Juan");

        // Smoke-check: valid Base64 string (ciphertext format is IV[12] || ciphertext+tag, Base64-encoded)
        assertThat(storedFirstName)
                .as("stored ciphertext should be a non-trivial Base64-encoded string")
                .hasSizeGreaterThan(20);
    }
}
