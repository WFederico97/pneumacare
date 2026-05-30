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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for patient identifier management business scenarios.
 *
 * <p>Spins up a real PostgreSQL container via Testcontainers (shared Spring context
 * with {@link PatientPiiEncryptionIT} and {@link IdentifierTypeIT} — same
 * {@code @SpringBootTest} properties). The full Spring MVC → service → JPA → DB
 * stack is exercised end-to-end.
 *
 * <p>The {@link wfederico.pneumacare.patient.infrastructure.IdentifierTypeDataSeeder}
 * seeds the identifier type catalog (DNI, CUIL, CUIT, LE, LC, Pasaporte) at
 * context startup, so all type IDs are resolved dynamically via the repository.
 *
 * <h3>Acceptance criteria covered</h3>
 * <ul>
 *   <li><strong>AC-DNI</strong>  — Standard DNI admission: patient registered and retrieved with
 *       correct DNI identifier type name and value.</li>
 *   <li><strong>AC-PASS</strong> — Foreign patient admission: patient registered with a Pasaporte
 *       identifier; typeName in response equals {@code "Pasaporte"}.</li>
 *   <li><strong>AC-DUP</strong>  — Duplicate identifier type prevention: a request containing the
 *       same {@code identifierTypeId} twice is rejected with {@code 400 Bad Request}.</li>
 * </ul>
 *
 * <h3>Additional scenarios</h3>
 * <ul>
 *   <li>Patient with multiple identifiers (DNI + CUIL) — all returned in response</li>
 *   <li>Validation: birth date in the future → 400</li>
 *   <li>Validation: first name exceeding 100 characters → 400</li>
 *   <li>Validation: identifier value exceeding 50 characters → 400</li>
 * </ul>
 */
@SpringBootTest(
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class PatientIdentifierManagementIT {

    @BeforeAll
    static void requireDocker() {
        assumeTrue(
                isDockerAvailable(),
                "Skipping PatientIdentifierManagementIT: no valid Docker environment found. " +
                "This test runs in CI via service containers.");
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
    private ObjectMapper objectMapper;

    @Autowired
    private PatientIdentifierTypeRepository identifierTypeRepository;

    /** Resolved from the DB at test setup — safe to use across all tests in this class. */
    private int dniTypeId;
    private int cuilTypeId;
    private int passportTypeId;

    @BeforeEach
    void resolveIdentifierTypeIds() {
        dniTypeId      = findTypeId("DNI");
        cuilTypeId     = findTypeId("CUIL");
        passportTypeId = findTypeId("Pasaporte");
    }

    // =========================================================================
    // AC-DNI — Standard DNI admission
    // =========================================================================

    @Test
    @DisplayName("AC-DNI — Standard admission: patient registered with DNI and retrieved correctly")
    void standardDniAdmission_patientCreatedAndRetrievedWithCorrectDni() throws Exception {
        // Register
        MvcResult createResult = mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Carlos",
                                  "lastName":  "García",
                                  "birthDate": "1985-03-15",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "28741236" }
                                  ]
                                }
                                """.formatted(dniTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Patient registered successfully"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.firstName").value("Carlos"))
                .andExpect(jsonPath("$.data.lastName").value("García"))
                .andExpect(jsonPath("$.data.birthDate").value("1985-03-15"))
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value("28741236"))
                .andReturn();

        UUID id = extractPatientId(createResult);

        // Retrieve
        mockMvc.perform(get("/api/v1/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Patient retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.firstName").value("Carlos"))
                .andExpect(jsonPath("$.data.lastName").value("García"))
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value("28741236"));
    }

    // =========================================================================
    // AC-PASS — Foreign patient admission
    // =========================================================================

    @Test
    @DisplayName("AC-PASS — Foreign patient admission: registered with Pasaporte identifier")
    void foreignPatientAdmission_patientCreatedWithPassportIdentifier() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Maria",
                                  "lastName":  "Rossi",
                                  "birthDate": "1992-07-20",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "YB987654" }
                                  ]
                                }
                                """.formatted(passportTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("Pasaporte"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value("YB987654"))
                .andReturn();

        UUID id = extractPatientId(createResult);

        // Verify retrieval preserves Pasaporte type name
        mockMvc.perform(get("/api/v1/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("Pasaporte"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value("YB987654"));
    }

    // =========================================================================
    // AC-DUP — Duplicate identifier type prevention
    // =========================================================================

    @Test
    @DisplayName("AC-DUP — Duplicate type prevention: same identifierTypeId twice in request returns 400")
    void duplicateIdentifierTypePrevention_sameTypeTwiceInRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Ana",
                                  "lastName":  "López",
                                  "birthDate": "1995-11-01",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "35000001" },
                                    { "identifierTypeId": %d, "value": "35000002" }
                                  ]
                                }
                                """.formatted(dniTypeId, dniTypeId)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Multiple identifiers
    // =========================================================================

    @Test
    @DisplayName("Multiple identifiers — DNI and CUIL both persisted and returned in response")
    void multipleIdentifiers_dniAndCuil_bothReturnedInResponse() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Luis",
                                  "lastName":  "Fernández",
                                  "birthDate": "1978-04-10",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "17654321" },
                                    { "identifierTypeId": %d, "value": "20176543219" }
                                  ]
                                }
                                """.formatted(dniTypeId, cuilTypeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.identifiers.length()").value(2))
                .andReturn();

        UUID id = extractPatientId(createResult);

        mockMvc.perform(get("/api/v1/patients/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifiers.length()").value(2))
                .andExpect(jsonPath("$.data.identifiers[?(@.typeName == 'DNI')]").isNotEmpty())
                .andExpect(jsonPath("$.data.identifiers[?(@.typeName == 'CUIL')]").isNotEmpty());
    }

    // =========================================================================
    // Validation — input boundary checks
    // =========================================================================

    @Test
    @DisplayName("Validation — birth date in the future is rejected with 400")
    void validation_futureBirthDate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Futura",
                                  "lastName":  "Persona",
                                  "birthDate": "2099-01-01",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "00000001" }
                                  ]
                                }
                                """.formatted(dniTypeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validation — firstName exceeding 100 characters is rejected with 400")
    void validation_firstNameExceedingMaxLength_returns400() throws Exception {
        String tooLong = "A".repeat(101);
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName":  "López",
                                  "birthDate": "1990-01-01",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "12345678" }
                                  ]
                                }
                                """.formatted(tooLong, dniTypeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validation — identifier value exceeding 50 characters is rejected with 400")
    void validation_identifierValueExceedingMaxLength_returns400() throws Exception {
        String tooLong = "X".repeat(51);
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pedro",
                                  "lastName":  "Sánchez",
                                  "birthDate": "1988-06-22",
                                  "identifiers": [
                                    { "identifierTypeId": %d, "value": "%s" }
                                  ]
                                }
                                """.formatted(passportTypeId, tooLong)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID extractPatientId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        String idStr = data.path("id").asString();
        assertThat(idStr).isNotBlank();
        return UUID.fromString(idStr);
    }

    private int findTypeId(String typeName) {
        return identifierTypeRepository.findAll().stream()
                .filter(t -> typeName.equals(t.getPatientIdentifierTypeName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Identifier type not found in catalog: " + typeName))
                .getPatientIdentifierTypeId();
    }
}
