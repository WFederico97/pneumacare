package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.TestcontainersConfiguration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/v1/identifier-types}.
 *
 * <p>Reuses the same application context as {@link PatientPiiEncryptionIT}
 * (identical {@code @SpringBootTest} properties) so Spring Test serves both
 * test classes from a single cached context, keeping the suite fast.
 *
 * <p>The {@link wfederico.pneumacare.patient.infrastructure.IdentifierTypeDataSeeder}
 * fires on context startup (dev profile) and pre-populates the catalog with
 * 6 types (DNI, CUIL, CUIT, LE, LC, Pasaporte). No per-test setup is needed.
 */
@SpringBootTest(
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class IdentifierTypeIT {

    @BeforeAll
    static void requireDocker() {
        assumeTrue(
            isDockerAvailable(),
            "Skipping IdentifierTypeIT: no valid Docker environment found. " +
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

    // -------------------------------------------------------------------------
    // Response structure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/identifier-types returns 200 with correct envelope")
    void listIdentifierTypes_returnsOkWithEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Identifier types retrieved successfully"))
                .andExpect(jsonPath("$.data").isArray());
    }

    // -------------------------------------------------------------------------
    // Catalog content — seeded by IdentifierTypeDataSeeder at context startup
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/identifier-types returns all 6 seeded types")
    void listIdentifierTypes_returnsAllSeededTypes() throws Exception {
        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    @DisplayName("GET /api/v1/identifier-types — DNI is first (seed insertion order)")
    void listIdentifierTypes_dniIsFirst() throws Exception {
        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("DNI"))
                .andExpect(jsonPath("$.data[0].description").value("Documento Nacional de Identidad"))
                .andExpect(jsonPath("$.data[0].id").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/identifier-types — all expected types are present")
    void listIdentifierTypes_allExpectedTypesPresent() throws Exception {
        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'DNI')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.name == 'CUIL')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.name == 'CUIT')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.name == 'LE')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.name == 'LC')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.name == 'Pasaporte')]").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/identifier-types — each entry has id, name and description")
    void listIdentifierTypes_eachEntryHasRequiredFields() throws Exception {
        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                // spot-check CUIL (second row)
                .andExpect(jsonPath("$.data[1].id").isNumber())
                .andExpect(jsonPath("$.data[1].name").value("CUIL"))
                .andExpect(jsonPath("$.data[1].description").value("Código Único de Identificación Laboral"));
    }
}
