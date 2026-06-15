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
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.procedures.application.AirwayEventService;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.web.AirwayEventController;
import wfederico.pneumacare.procedures.web.dto.AirwayEventResponse;
import wfederico.pneumacare.procedures.web.dto.CreateAirwayEventRequest;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AirwayEventController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class AirwayEventControllerTest {

    private static final String POST_URL = "/api/v1/procedures/airway";
    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AirwayEventService service;

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

    private static AirwayEventResponse intubationResponse() {
        return new AirwayEventResponse(EVENT_ID, PATIENT_ID, SHIFT_ID, AirwayEventType.INTUBATION,
                RespiratoryStatus.INTUBATED, OffsetDateTime.now(ZoneOffset.UTC), CHIEF_ID,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static String validBody() {
        return """
                { "patientId": "%s", "eventType": "INTUBATION", "eventTimestamp": "2026-06-13T09:30:00Z" }
                """.formatted(PATIENT_ID);
    }

    @Test
    @DisplayName("register valid event returns 201 with Location and resulting status")
    void registerAirwayEvent_valid_returns201WithLocation() throws Exception {
        when(service.register(any(CreateAirwayEventRequest.class))).thenReturn(intubationResponse());

        mockMvc.perform(post(POST_URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/" + PATIENT_ID + "/airway-events"))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Evento de vía aérea registrado exitosamente"))
                .andExpect(jsonPath("$.data.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.data.eventType").value("INTUBATION"))
                .andExpect(jsonPath("$.data.resultingStatus").value("INTUBATED"));
    }

    @Test
    @DisplayName("register with missing fields returns 400")
    void registerAirwayEvent_missingFields_returns400() throws Exception {
        mockMvc.perform(post(POST_URL).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("register for unknown patient returns 404")
    void registerAirwayEvent_unknownPatient_returns404() throws Exception {
        when(service.register(any(CreateAirwayEventRequest.class)))
                .thenThrow(new BusinessLayerException("No se encontró el paciente con id: " + PATIENT_ID, NOT_FOUND));

        mockMvc.perform(post(POST_URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("register without an OPEN shift returns 409")
    void registerAirwayEvent_noOpenShift_returns409() throws Exception {
        when(service.register(any(CreateAirwayEventRequest.class)))
                .thenThrow(new BusinessLayerException("No hay un turno abierto para la UCI del paciente", CONFLICT));

        mockMvc.perform(post(POST_URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("register an illegal transition returns 409")
    void registerAirwayEvent_illegalTransition_returns409() throws Exception {
        when(service.register(any(CreateAirwayEventRequest.class)))
                .thenThrow(new BusinessLayerException("Transición de vía aérea no permitida", CONFLICT));

        mockMvc.perform(post(POST_URL).contentType(APPLICATION_JSON).content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("list patient airway events returns 200 with the events")
    void getPatientAirwayEvents_returns200WithList() throws Exception {
        when(service.getPatientAirwayEvents(PATIENT_ID)).thenReturn(List.of(intubationResponse()));

        mockMvc.perform(get("/api/v1/patients/" + PATIENT_ID + "/airway-events").accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Eventos de vía aérea recuperados exitosamente"))
                .andExpect(jsonPath("$.data[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].resultingStatus").value("INTUBATED"));
    }

    @Test
    @DisplayName("list events for unknown patient returns 404")
    void getPatientAirwayEvents_unknownPatient_returns404() throws Exception {
        when(service.getPatientAirwayEvents(PATIENT_ID))
                .thenThrow(new BusinessLayerException("No se encontró el paciente con id: " + PATIENT_ID, NOT_FOUND));

        mockMvc.perform(get("/api/v1/patients/" + PATIENT_ID + "/airway-events").accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
