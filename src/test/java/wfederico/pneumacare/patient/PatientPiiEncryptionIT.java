package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;

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
 *   <li><strong>AC1</strong> — PII fields stored as encrypted Base64 in the DB
 *       (covers both {@code patient_identities} name fields and
 *       {@code patient_identifiers.patient_identifier_name})</li>
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

    @Autowired
    private PatientIdentifierTypeRepository identifierTypeRepository;

    private static final String FIRST_NAME       = "Juan";
    private static final String LAST_NAME        = "Pérez";
    private static final String BIRTH_DATE       = "1990-05-20";
    private static final String IDENTIFIER_VALUE = "12345678";

    /** Primary key of the DNI type row created in {@link #seedIdentifierType()}. */
    private int dniTypeId;

    /**
     * Ensures a "DNI" identifier type exists before each test.
     * Reuses an existing row if the type was already seeded by a previous test
     * in the same Spring context.
     */
    @BeforeEach
    void seedIdentifierType() {
        PatientIdentifierTypeJpaEntity dniType = identifierTypeRepository.findAll().stream()
                .filter(t -> "DNI".equals(t.getPatientIdentifierTypeName()))
                .findFirst()
                .orElseGet(() -> identifierTypeRepository.save(
                        PatientIdentifierTypeJpaEntity.builder()
                                .patientIdentifierTypeName("DNI")
                                .patientIdentifierTypeDescription("Documento Nacional de Identidad")
                                .build()));
        dniTypeId = dniType.getPatientIdentifierTypeId();
    }

    private String requestBody(String firstName, String lastName, String birthDate) {
        return """
                {
                    "firstName":   "%s",
                    "lastName":    "%s",
                    "birthDate":   "%s",
                    "identifiers": [
                        { "identifierTypeId": %d, "value": "%s" }
                    ]
                }
                """.formatted(firstName, lastName, birthDate, dniTypeId, IDENTIFIER_VALUE);
    }

    // -------------------------------------------------------------------------
    // AC1 — encrypted at rest
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AC1 — Name fields are stored as AES-256-GCM encrypted Base64 in patient_identities")
    void piiFieldsAreStoredEncryptedInDatabase() throws Exception {
        UUID id = createPatientAndGetId(FIRST_NAME, LAST_NAME);

        // Query raw column values — bypasses the JPA AttributeConverter
        Map<String, Object> rawRow = jdbcTemplate.queryForMap(
                "SELECT first_name, last_name FROM patient_identities WHERE id = ?",
                id);

        String rawFirstName = (String) rawRow.get("first_name");
        String rawLastName  = (String) rawRow.get("last_name");

        // Must not equal plaintext
        assertThat(rawFirstName).isNotEqualTo(FIRST_NAME);
        assertThat(rawLastName).isNotEqualTo(LAST_NAME);

        // Must be valid Base64
        assertThatCode(() -> Base64.getDecoder().decode(rawFirstName)).doesNotThrowAnyException();
        assertThatCode(() -> Base64.getDecoder().decode(rawLastName)).doesNotThrowAnyException();

        // Decoded bytes must be larger than plaintext (IV + ciphertext + auth-tag)
        assertThat(Base64.getDecoder().decode(rawFirstName).length)
                .isGreaterThan(FIRST_NAME.getBytes().length);
        assertThat(Base64.getDecoder().decode(rawLastName).length)
                .isGreaterThan(LAST_NAME.getBytes().length);
    }

    @Test
    @DisplayName("AC1 — Identifier value is stored as AES-256-GCM encrypted Base64 in patient_identifiers")
    void identifierValueIsStoredEncryptedInDatabase() throws Exception {
        UUID id = createPatientAndGetId(FIRST_NAME, LAST_NAME);

        // Query the raw identifier value — bypasses the JPA AttributeConverter
        String rawIdentifierName = (String) jdbcTemplate.queryForMap(
                "SELECT patient_identifier_name FROM patient_identifiers WHERE patient_identity_id = ?",
                id).get("patient_identifier_name");

        // Must not equal plaintext
        assertThat(rawIdentifierName).isNotEqualTo(IDENTIFIER_VALUE);

        // Must be valid Base64
        assertThatCode(() -> Base64.getDecoder().decode(rawIdentifierName)).doesNotThrowAnyException();

        // Decoded bytes must be larger than plaintext (IV + ciphertext + auth-tag)
        assertThat(Base64.getDecoder().decode(rawIdentifierName).length)
                .isGreaterThan(IDENTIFIER_VALUE.getBytes().length);
    }

    @Test
    @DisplayName("AC1 — Same plaintext produces different ciphertext on each insert (random IV)")
    void sameNameEncryptsDifferentlyOnEachInsert() throws Exception {
        UUID id1 = createPatientAndGetId(FIRST_NAME, LAST_NAME);
        UUID id2 = createPatientAndGetId(FIRST_NAME, LAST_NAME);

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
        UUID id = createPatientAndGetId(FIRST_NAME, LAST_NAME);

        mockMvc.perform(get("/api/v1/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.birthDate").value(BIRTH_DATE))
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value(IDENTIFIER_VALUE));
    }

    @Test
    @DisplayName("AC2 — POST /api/v1/patients response contains plain-text PII (no encrypted blobs)")
    void postResponseContainsPlainTextPii() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(FIRST_NAME, LAST_NAME, BIRTH_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value(IDENTIFIER_VALUE))
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
                                { "firstName": "", "lastName": "", "birthDate": "1990-05-20",
                                  "identifiers": [{ "identifierTypeId": %d, "value": "x" }] }
                                """.formatted(dniTypeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/patients returns 400 when identifiers list is missing")
    void postWithMissingIdentifiersReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Juan", "lastName": "Pérez", "birthDate": "1990-05-20" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/patients returns 400 when identifier type does not exist")
    void postWithUnknownIdentifierTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Juan", "lastName": "Pérez", "birthDate": "1990-05-20",
                                  "identifiers": [{ "identifierTypeId": 99999, "value": "12345678" }] }
                                """))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID createPatientAndGetId(String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(firstName, lastName, BIRTH_DATE)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return UUID.fromString(data.path("id").asString());
    }
}
