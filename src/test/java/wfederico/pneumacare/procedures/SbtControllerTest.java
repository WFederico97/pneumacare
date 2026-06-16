package wfederico.pneumacare.procedures;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.procedures.application.SbtService;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.procedures.web.SbtController;
import wfederico.pneumacare.procedures.web.dto.CreateSbtRequest;
import wfederico.pneumacare.procedures.web.dto.SbtResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = SbtController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class SbtControllerTest {

    private static final String URL = "/api/v1/procedures/sbt";
    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID SBT_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SbtService service;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUpMocks() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.expire(anyString(), anyLong(), any(java.util.concurrent.TimeUnit.class)))
                .thenReturn(true);
    }

    private static SbtResponse sbtResponse() {
        return new SbtResponse(SBT_ID, PATIENT_ID, SHIFT_ID, 30, ToleranceResult.SUCCESS,
                CHIEF_ID, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static String validBody() {
        return """
                { "patientId": "%s", "durationMinutes": 30, "toleranceResult": "SUCCESS" }
                """.formatted(PATIENT_ID);
    }

    @Test
    @DisplayName("record valid SBT returns 201 with Location and body")
    void recordSbt_valid_returns201WithLocation() throws Exception {
        when(service.register(any(CreateSbtRequest.class))).thenReturn(sbtResponse());

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", URL + "?patientId=" + PATIENT_ID))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("SBT registrado exitosamente"))
                .andExpect(jsonPath("$.data.id").value(SBT_ID.toString()))
                .andExpect(jsonPath("$.data.durationMinutes").value(30))
                .andExpect(jsonPath("$.data.toleranceResult").value("SUCCESS"));
    }

    @Test
    @DisplayName("record with missing fields returns 400")
    void recordSbt_missingFields_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("record with non-positive duration returns 422")
    void recordSbt_zeroDuration_returns422() throws Exception {
        when(service.register(any(CreateSbtRequest.class)))
                .thenThrow(new BusinessLayerException(
                        "La duración del SBT debe ser un entero positivo (mayor a 0)", UNPROCESSABLE_CONTENT));

        String body = """
                { "patientId": "%s", "durationMinutes": 0, "toleranceResult": "SUCCESS" }
                """.formatted(PATIENT_ID);

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("record for unknown patient returns 404")
    void recordSbt_unknownPatient_returns404() throws Exception {
        when(service.register(any(CreateSbtRequest.class)))
                .thenThrow(new BusinessLayerException("No se encontró el paciente con id: " + PATIENT_ID, NOT_FOUND));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("record without an OPEN shift returns 409")
    void recordSbt_noOpenShift_returns409() throws Exception {
        when(service.register(any(CreateSbtRequest.class)))
                .thenThrow(new BusinessLayerException("No hay un turno abierto para la UCI del paciente", CONFLICT));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("list SBT history returns 200 with the trials")
    void getHistory_returns200WithList() throws Exception {
        when(service.getHistory(eq(PATIENT_ID))).thenReturn(List.of(sbtResponse()));

        mockMvc.perform(get(URL).param("patientId", PATIENT_ID.toString()).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Historial de SBT recuperado exitosamente"))
                .andExpect(jsonPath("$.data[0].id").value(SBT_ID.toString()))
                .andExpect(jsonPath("$.data[0].toleranceResult").value("SUCCESS"));
    }

    @Test
    @DisplayName("list history for unknown patient returns 404")
    void getHistory_unknownPatient_returns404() throws Exception {
        when(service.getHistory(eq(PATIENT_ID)))
                .thenThrow(new BusinessLayerException("No se encontró el paciente con id: " + PATIENT_ID, NOT_FOUND));

        mockMvc.perform(get(URL).param("patientId", PATIENT_ID.toString()).accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
