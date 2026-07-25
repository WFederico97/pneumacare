package wfederico.pneumacare.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.clinical.application.EvaluationPersistenceService;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.DrivingPressureBand;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.web.EvaluationController;
import wfederico.pneumacare.clinical.web.dto.EvaluationResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

/**
 * Web-layer unit tests for {@link EvaluationController}.
 *
 * <p>Uses {@code @WebMvcTest} — no Spring Data, no Testcontainers, no Docker.
 * {@link EvaluationPersistenceService} is mocked via {@code @MockitoBean}.
 * {@link StringRedisTemplate} is mocked to satisfy {@link SecurityConfig} without Redis.
 *
 * <p>{@link SecurityConfig} is imported explicitly so that
 * {@code @EnableMethodSecurity} and {@code devSecurityFilterChain} are active.
 * The {@code dev} profile activates {@code devSecurityFilterChain} which grants
 * {@code permitAll()} to {@code /api/**} — no role or token is required.
 *
 * <h3>BDD scenarios covered</h3>
 * <ul>
 *   <li>Scenario 1 — valid payload + THERAPIST role → 201 with rsbi_snapshot</li>
 *   <li>Scenario 2 — no auth header (anonymous, dev profile) → 201</li>
 *   <li>Scenario 3 — missing {@code pao2} → 400 with field error</li>
 *   <li>Extra — pplat ≤ peep (service throws 400) → propagates 400</li>
 *   <li>Extra — THERAPIST role but missing {@code patientId} → 400</li>
 * </ul>
 */
@WebMvcTest(
        value = EvaluationController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class EvaluationControllerTest {

    private static final String URL = "/api/v1/evaluations";

    private static final UUID PATIENT_ID    = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SHIFT_ID      = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID VENTILATOR_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID CREATED_BY    = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationPersistenceService service;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /** A valid minimal JSON request body. */
    private static final String VALID_BODY = """
            {
              "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
              "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
              "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
              "brand": "TECME",
              "f":    15,
              "vt":   500,
              "pao2": 85,
              "fio2": 0.40,
              "pplat": 25,
              "peep":   5
            }
            """;

    @BeforeEach
    void setUpMocks() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);
    }

    // ── Scenario 1: happy path ─────────────────────────────────────────────

    @Test
    @DisplayName("createEvaluation_validPayloadWithTherapistRole_returns201WithRsbiSnapshot")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_validPayload_returns201() throws Exception {
        EvaluationResponse stub = new EvaluationResponse(
                UUID.randomUUID(),
                PATIENT_ID,
                SHIFT_ID,
                VENTILATOR_ID,
                OffsetDateTime.now(),
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("0.40"),
                new BigDecimal("25"),
                new BigDecimal("5"),
                new BigDecimal("30.00"),
                RsbiInterpretation.FAVORABLE,
                new BigDecimal("212.50"),
                PafiClassification.MILD_ARDS,
                new BigDecimal("25.00"),
                CstatInterpretation.LOW,
                new BigDecimal("20"),
                DrivingPressureBand.HIGH,
                false,
                CREATED_BY);

        when(service.create(any())).thenReturn(stub);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Evaluación registrada exitosamente"))
                .andExpect(jsonPath("$.data.rsbiSnapshot").value(30.00))
                .andExpect(jsonPath("$.data.rsbiInterpretation").value("FAVORABLE"))
                .andExpect(jsonPath("$.data.pafiSnapshot").value(212.50))
                .andExpect(jsonPath("$.data.pafiClassification").value("MILD_ARDS"))
                .andExpect(jsonPath("$.data.cstatSnapshot").value(25.00))
                .andExpect(jsonPath("$.data.cstatInterpretation").value("LOW"))
                .andExpect(jsonPath("$.data.patientId").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$.data.createdBy").value(CREATED_BY.toString()));
    }

    // ── Scenario 2: no auth header — dev profile permits anonymous ────────

    @Test
    @DisplayName("createEvaluation_noAuthHeader_devProfile_returns201")
    void createEvaluation_noAuth_devProfile_returns201() throws Exception {
        EvaluationResponse stub = new EvaluationResponse(
                UUID.randomUUID(),
                PATIENT_ID,
                SHIFT_ID,
                VENTILATOR_ID,
                OffsetDateTime.now(),
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("0.40"),
                new BigDecimal("25"),
                new BigDecimal("5"),
                new BigDecimal("30.00"),
                RsbiInterpretation.FAVORABLE,
                new BigDecimal("212.50"),
                PafiClassification.MILD_ARDS,
                new BigDecimal("25.00"),
                CstatInterpretation.LOW,
                new BigDecimal("20"),
                DrivingPressureBand.HIGH,
                false,
                CREATED_BY);

        when(service.create(any())).thenReturn(stub);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201));
    }

    // ── Scenario 3: missing pao2 → 400 ────────────────────────────────────

    @Test
    @DisplayName("createEvaluation_missingPao2_returns400WithFieldError")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_missingPao2_returns400() throws Exception {
        String bodyMissingPao2 = """
                {
                  "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
                  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                  "brand": "TECME",
                  "f":    15,
                  "vt":   500,
                  "fio2": 0.40,
                  "pplat": 25,
                  "peep":   5
                }
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingPao2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.pao2").exists());
    }

    // ── Extra: pplat ≤ peep → DTO @AssertTrue rejects with field-level 400 ─

    @Test
    @DisplayName("createEvaluation_pplatLessThanOrEqualPeep_returns400WithPplatFieldError")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_pplatLessThanPeep_returns400() throws Exception {
        // pplat == peep — the DTO's @AssertTrue isPplatGreaterThanPeep() catches
        // this before the request ever reaches the service. The bean-validation
        // path produces a per-field error map keyed on "pplatGreaterThanPeep"
        // (the property name derived from the validator method).
        String bodyBadPressures = """
                {
                  "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
                  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                  "brand": "TECME",
                  "f":    15,
                  "vt":   500,
                  "pao2": 85,
                  "fio2": 0.40,
                  "pplat": 5,
                  "peep":  5
                }
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyBadPressures))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.pplatGreaterThanPeep").exists());
    }

    // ── Extra: missing patientId → 400 ────────────────────────────────────

    @Test
    @DisplayName("createEvaluation_missingPatientId_returns400WithFieldError")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_missingPatientId_returns400() throws Exception {
        String bodyMissingPatientId = """
                {
                  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
                  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                  "brand": "TECME",
                  "f":    15,
                  "vt":   500,
                  "pao2": 85,
                  "fio2": 0.40,
                  "pplat": 25,
                  "peep":   5
                }
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingPatientId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.patientId").exists());
    }

    // ── Extra: f out of range → 400 ───────────────────────────────────────

    @Test
    @DisplayName("createEvaluation_respiratoryRateAboveMax_returns400WithFieldError")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_fAboveMax_returns400() throws Exception {
        String bodyBadF = """
                {
                  "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
                  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                  "brand": "TECME",
                  "f":    99,
                  "vt":   500,
                  "pao2": 85,
                  "fio2": 0.40,
                  "pplat": 25,
                  "peep":   5
                }
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyBadF))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.f").exists());
    }

    // ── Extra: missing brand → 400 ────────────────────────────────────────

    @Test
    @DisplayName("createEvaluation_missingBrand_returns400WithBrandFieldError")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_missingBrand_returns400() throws Exception {
        String bodyMissingBrand = """
                {
                  "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
                  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                  "f":    15,
                  "vt":   500,
                  "pao2": 85,
                  "fio2": 0.40,
                  "pplat": 25,
                  "peep":   5
                }
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingBrand))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.brand").exists());
    }

    // ── Extra: invalid brand → 400 ────────────────────────────────────────

    @Test
    @DisplayName("createEvaluation_unknownBrand_returns400")
    @WithMockUser(roles = "THERAPIST")
    void createEvaluation_unknownBrand_returns400() throws Exception {
        String bodyUnknownBrand = """
                {
                  "patientId":            "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "shiftId":              "bbbbbbbb-0000-0000-0000-000000000001",
                  "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                  "brand": "PUREMA",
                  "f":    15,
                  "vt":   500,
                  "pao2": 85,
                  "fio2": 0.40,
                  "pplat": 25,
                  "peep":   5
                }
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyUnknownBrand))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
