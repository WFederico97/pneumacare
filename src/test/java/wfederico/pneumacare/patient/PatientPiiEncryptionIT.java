package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import wfederico.pneumacare.TestcontainersConfiguration;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for transparent AES-256-GCM encryption of patient PII.
 *
 * <p>Spins up a real PostgreSQL container via Testcontainers, starts the full
 * Spring Boot context, and exercises the complete stack from HTTP through
 * Spring MVC → service → JPA → PostgreSQL.
 *
 * <h3>Acceptance criteria covered</h3>
 * <ul>
 *   <li><strong>AC1</strong> — PII fields stored as encrypted Base64 in the DB</li>
 *   <li><strong>AC2</strong> — PII fields returned as plain text via the REST API</li>
 *   <li><strong>AC3</strong> — Startup validation covered by {@code AesEncryptionConfigTest}</li>
 * </ul>
 *
 * <h3>Test AES key</h3>
 * {@code AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=} = Base64(0x00 × 32 bytes).
 * Valid for test purposes only — never use in production.
 */
@SpringBootTest(
        properties = {
                // 32-byte (256-bit) test key: Base64(0x00 * 32) = 43 A's + "="
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class PatientPiiEncryptionIT {

    @BeforeAll
    static void requireDocker() {
        assumeTrue(
            isDockerAvailable(),
            "Skipping PatientPiiEncryptionIT: no valid Docker environment found " +
            "(Docker Desktop CLI-proxy mode is not supported by docker-java/Testcontainers on this host). " +
            "This test runs in CI via service containers."
        );
    }

    private static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;  // tools.jackson.databind.ObjectMapper

    private static final String FIRST_NAME  = "Juan";
    private static final String LAST_NAME   = "Pérez";
    private static final String NATIONAL_ID = "12345678";
    private static final String BIRTH_DATE  = "1990-05-20";

    private static String requestBody(String firstName, String lastName,
                                       String nationalId, String birthDate) {
        return """
                {
                    "firstName":  "%s",
                    "lastName":   "%s",
                    "nationalId": "%s",
                    "birthDate":  "%s"
                }
                """.formatted(firstName, lastName, nationalId, birthDate);
    }

    // -------------------------------------------------------------------------
    // AC1 — encrypted at rest
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC1 — PII fields are stored as AES-256-GCM encrypted Base64 in the database")
    void piiFieldsAreStoredEncryptedInDatabase() throws Exception {
        UUID id = createPatientAndGetId(FIRST_NAME, LAST_NAME, NATIONAL_ID);

        // Query raw column values — bypasses the JPA AttributeConverter
        Map<String, Object> rawRow = jdbcTemplate.queryForMap(
                "SELECT first_name, last_name, national_id FROM patient_identities WHERE id = ?",
                id);

        String rawFirstName  = (String) rawRow.get("first_name");
        String rawLastName   = (String) rawRow.get("last_name");
        String rawNationalId = (String) rawRow.get("national_id");

        // Must not equal plaintext
        assertThat(rawFirstName).isNotEqualTo(FIRST_NAME);
        assertThat(rawLastName).isNotEqualTo(LAST_NAME);
        assertThat(rawNationalId).isNotEqualTo(NATIONAL_ID);

        // Must be valid Base64
        assertThatCode(() -> Base64.getDecoder().decode(rawFirstName)).doesNotThrowAnyException();
        assertThatCode(() -> Base64.getDecoder().decode(rawLastName)).doesNotThrowAnyException();
        assertThatCode(() -> Base64.getDecoder().decode(rawNationalId)).doesNotThrowAnyException();

        // Decoded bytes must be larger than plaintext (IV + ciphertext + auth-tag)
        assertThat(Base64.getDecoder().decode(rawFirstName).length)
                .isGreaterThan(FIRST_NAME.getBytes().length);
        assertThat(Base64.getDecoder().decode(rawNationalId).length)
                .isGreaterThan(NATIONAL_ID.getBytes().length);
    }

    @Test
    @DisplayName("AC1 — Same plaintext produces different ciphertext on each insert (random IV)")
    void sameNameEncryptsDifferentlyOnEachInsert() throws Exception {
        UUID id1 = createPatientAndGetId(FIRST_NAME, LAST_NAME, "11111111");
        UUID id2 = createPatientAndGetId(FIRST_NAME, LAST_NAME, "22222222");

        String enc1 = (String) jdbcTemplate.queryForMap(
                "SELECT first_name FROM patient_identities WHERE id = ?", id1).get("first_name");
        String enc2 = (String) jdbcTemplate.queryForMap(
                "SELECT first_name FROM patient_identities WHERE id = ?", id2).get("first_name");

        // Non-deterministic encryption — same plaintext, different ciphertext
        assertThat(enc1).isNotEqualTo(enc2);
    }

    // -------------------------------------------------------------------------
    // AC2 — decrypted via API
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC2 — GET /api/v1/patients/{id} returns PII fields as plain text")
    void piiFieldsAreReturnedDecryptedViaApi() throws Exception {
        UUID id = createPatientAndGetId(FIRST_NAME, LAST_NAME, NATIONAL_ID);

        mockMvc.perform(get("/api/v1/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.nationalId").value(NATIONAL_ID))
                .andExpect(jsonPath("$.data.birthDate").value(BIRTH_DATE));
    }

    @Test
    @DisplayName("AC2 — POST /api/v1/patients response contains plain-text PII (no encrypted blobs)")
    void postResponseContainsPlainTextPii() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(FIRST_NAME, LAST_NAME, NATIONAL_ID, BIRTH_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.nationalId").value(NATIONAL_ID))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/patients/{id} returns 404 for unknown UUID")
    void getUnknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/patients/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/patients returns 400 when required PII fields are blank")
    void postWithBlankFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "", "lastName": "", "nationalId": "", "birthDate": "1990-05-20" }
                                """))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID createPatientAndGetId(String firstName, String lastName, String nationalId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(firstName, lastName, nationalId, BIRTH_DATE)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return UUID.fromString(data.path("id").asText());
    }
}
