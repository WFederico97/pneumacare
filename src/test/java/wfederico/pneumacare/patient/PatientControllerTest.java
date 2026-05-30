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
import java.util.List;
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
 * {@link StringRedisTemplate} is mocked to satisfy the {@code SecurityConfig}
 * constructor without a live Redis connection.
 *
 * <p>The {@code dev} profile activates {@code devSecurityFilterChain} which
 * grants {@code permitAll()} to {@code /api/**}, so no auth token is needed.
 * {@link wfederico.pneumacare.shared.exception.GlobalExceptionHandler} is also
 * loaded by {@code @WebMvcTest} as a {@code @RestControllerAdvice}, so
 * {@link BusinessLayerException}s thrown by the mocked service are translated
 * to the correct HTTP status codes.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>POST /api/v1/patients — valid request returns 201 with patient envelope</li>
 *   <li>POST /api/v1/patients — missing firstName returns 400 with field error map</li>
 *   <li>POST /api/v1/patients — empty identifiers list returns 400</li>
 *   <li>POST /api/v1/patients — service throws 400 (duplicate type) propagates 400</li>
 *   <li>GET /api/v1/patients/{id} — existing patient returns 200 with patient envelope</li>
 *   <li>GET /api/v1/patients/{id} — unknown UUID returns 404</li>
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

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static final UUID   PATIENT_ID  = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final String FIRST_NAME  = "Juan";
    private static final String LAST_NAME   = "Pérez";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1989, 5, 14);

    private static PatientResponse sampleResponse() {
        return new PatientResponse(
                PATIENT_ID, FIRST_NAME, LAST_NAME, BIRTH_DATE,
                List.of(new PatientIdentifierResponse("DNI", "35123456")));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/patients
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/patients — valid request returns 201 with patient data")
    void createPatient_validRequest_returns201WithPatientData() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse());

        String requestJson = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifiers": [{ "identifierTypeId": 1, "value": "35123456" }]
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Patient registered successfully"))
                .andExpect(jsonPath("$.data.id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.birthDate").value("1989-05-14"))
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value("35123456"));
    }

    @Test
    @DisplayName("POST /api/v1/patients — missing firstName returns 400 with field error")
    void createPatient_missingFirstName_returns400WithFieldError() throws Exception {
        String requestJson = """
                {
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifiers": [{ "identifierTypeId": 1, "value": "35123456" }]
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.firstName").exists());
    }

    @Test
    @DisplayName("POST /api/v1/patients — empty identifiers list returns 400")
    void createPatient_emptyIdentifiersList_returns400() throws Exception {
        String requestJson = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifiers": []
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/patients — service throws BAD_REQUEST (duplicate type) returns 400")
    void createPatient_serviceDuplicateType_returns400() throws Exception {
        when(service.create(any())).thenThrow(
                new BusinessLayerException("Duplicate identifier type in request", HttpStatus.BAD_REQUEST));

        String requestJson = """
                {
                  "firstName": "Juan",
                  "lastName": "Pérez",
                  "birthDate": "1989-05-14",
                  "identifiers": [
                    { "identifierTypeId": 1, "value": "35123456" },
                    { "identifierTypeId": 1, "value": "99999999" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Duplicate identifier type in request"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/patients/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/patients/{id} — existing patient returns 200 with patient data")
    void getPatient_existingId_returns200WithPatientData() throws Exception {
        when(service.findById(PATIENT_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/patients/{id}", PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Patient retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data.firstName").value(FIRST_NAME))
                .andExpect(jsonPath("$.data.lastName").value(LAST_NAME))
                .andExpect(jsonPath("$.data.identifiers[0].typeName").value("DNI"))
                .andExpect(jsonPath("$.data.identifiers[0].value").value("35123456"));
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
}
