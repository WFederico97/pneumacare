package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.patient.application.PatientIdentityService;
import wfederico.pneumacare.patient.web.PatientController;
import wfederico.pneumacare.patient.web.dto.PatientIdentifierResponse;
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer unit tests for {@link PatientController}.
 *
 * <p>Uses {@code @WebMvcTest} — no Spring Data, no Testcontainers, no Docker.
 * {@link PatientIdentityService} is mocked with {@code @MockitoBean}.
 * {@link StringRedisTemplate} is mocked to satisfy {@code SecurityConfig} without Redis.
 *
 * <p>The {@code dev} profile activates {@code devSecurityFilterChain} which grants
 * {@code permitAll()} to {@code /api/**}, so no auth token is needed.
 * {@link wfederico.pneumacare.shared.exception.GlobalExceptionHandler} is also
 * loaded by {@code @WebMvcTest} as a {@code @RestControllerAdvice}.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>POST — valid admission request returns 201 with new response shape</li>
 *   <li>POST — missing firstName returns 400</li>
 *   <li>POST — missing identifier object returns 400</li>
 *   <li>POST — missing bedId returns 400</li>
 *   <li>POST — missing icuId returns 400</li>
 *   <li>POST — service throws 400 (bed not available) propagates 400</li>
 *   <li>POST — service throws 404 (ICU not found) propagates 404</li>
 *   <li>GET /{id} — existing patient returns 200 with new response shape</li>
 *   <li>GET /{id} — unknown UUID returns 404</li>
 * </ul>
 */
@WebMvcTest(
        value = PatientController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientIdentityService service;

    /** Satisfies SecurityConfig constructor — no live Redis needed. */
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

    private static final UUID PATIENT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID ICU_ID     = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID BED_ID     = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    private static final String FIRST_NAME  = "Juan";
    private static final String LAST_NAME   = "Pérez";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1989, 5, 14);

    private static PatientResponse sampleResponse() {
        return new PatientResponse(
                PATIENT_ID,
                FIRST_NAME,
                LAST_NAME,
                BIRTH_DATE,
                new PatientIdentifierResponse("DNI", "35123456"),
                ICU_ID,
                BED_ID,
                OffsetDateTime.parse("2026-06-06T10:00:00-03:00"),
                "ADMITTED");
    }

    /** A minimal valid admission JSON with all required fields. */
    private static String validAdmissionJson() {
        return """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifier": { "identifierTypeId": 1, "value": "35123456" },
                  "icuId": "cccccccc-0000-0000-0000-000000000001",
                  "bedId": "dddddddd-0000-0000-0000-000000000001"
                }
                """;
    }

    // ── POST /api/v1/patients ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/patients — valid request returns 201 with admission data")
    void createPatient_validRequest_returns201WithAdmissionData() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdmissionJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Patient admitted successfully"))
                .andExpect(jsonPath("$.data.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.birthDate").value("1989-05-14"))
                .andExpect(jsonPath("$.data.identifier.typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifier.value").value("35123456"))
                .andExpect(jsonPath("$.data.icuId").value(ICU_ID.toString()))
                .andExpect(jsonPath("$.data.bedId").value(BED_ID.toString()))
                .andExpect(jsonPath("$.data.clinicalStatus").value("ADMITTED"));
    }

    @Test
    @DisplayName("POST /api/v1/patients — missing firstName returns 400 with field error")
    void createPatient_missingFirstName_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifier": { "identifierTypeId": 1, "value": "35123456" },
                  "icuId": "cccccccc-0000-0000-0000-000000000001",
                  "bedId": "dddddddd-0000-0000-0000-000000000001"
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.firstName").exists());
    }

    @Test
    @DisplayName("POST /api/v1/patients — missing identifier object returns 400 with field error")
    void createPatient_missingIdentifier_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "icuId": "cccccccc-0000-0000-0000-000000000001",
                  "bedId": "dddddddd-0000-0000-0000-000000000001"
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.identifier").exists());
    }

    @Test
    @DisplayName("POST /api/v1/patients — missing bedId returns 400 with field error")
    void createPatient_missingBedId_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifier": { "identifierTypeId": 1, "value": "35123456" },
                  "icuId": "cccccccc-0000-0000-0000-000000000001"
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.bedId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/patients — missing icuId returns 400 with field error")
    void createPatient_missingIcuId_returns400WithFieldError() throws Exception {
        String body = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifier": { "identifierTypeId": 1, "value": "35123456" },
                  "bedId": "dddddddd-0000-0000-0000-000000000001"
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.icuId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/patients — service throws BAD_REQUEST (bed not available) returns 400")
    void createPatient_bedNotAvailable_returns400() throws Exception {
        when(service.create(any())).thenThrow(
                new BusinessLayerException("La cama solicitada no está disponible. Estado actual: OCCUPIED",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdmissionJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "La cama solicitada no está disponible. Estado actual: OCCUPIED"));
    }

    @Test
    @DisplayName("POST /api/v1/patients — service throws NOT_FOUND (ICU not found) returns 404")
    void createPatient_icuNotFound_returns404() throws Exception {
        when(service.create(any())).thenThrow(
                new BusinessLayerException(
                        "No se encontró la UCI con id: cccccccc-0000-0000-0000-000000000001",
                        HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAdmissionJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/v1/patients/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/patients/{id} — existing patient returns 200 with admission data")
    void getPatient_existingId_returns200WithAdmissionData() throws Exception {
        when(service.findById(PATIENT_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/patients/{id}", PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Patient retrieved successfully"))
                .andExpect(jsonPath("$.data.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.identifier.typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifier.value").value("35123456"))
                .andExpect(jsonPath("$.data.bedId").value(BED_ID.toString()))
                .andExpect(jsonPath("$.data.clinicalStatus").value("ADMITTED"));
    }

    @Test
    @DisplayName("GET /api/v1/patients/{id} — non-existent UUID returns 404")
    void getPatient_nonExistentId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(service.findById(unknownId)).thenThrow(
                new BusinessLayerException("Patient not found with id: " + unknownId, HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/patients/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/patients — returns the patient list")
    void listPatients_returnsAll() throws Exception {
        when(service.findAll()).thenReturn(java.util.List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].firstName").value(FIRST_NAME));
    }
}
