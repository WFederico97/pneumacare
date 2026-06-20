package wfederico.pneumacare.timeline;

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
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;
import wfederico.pneumacare.timeline.application.TimelineService;
import wfederico.pneumacare.timeline.domain.TimelineEventType;
import wfederico.pneumacare.timeline.web.TimelineController;
import wfederico.pneumacare.timeline.web.dto.TimelineEntryResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = TimelineController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class TimelineControllerTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimelineService service;

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

    private static String url() {
        return "/api/v1/patients/" + PATIENT_ID + "/timeline";
    }

    @Test
    @DisplayName("returns 200 with the merged timeline in the response envelope")
    void getTimeline_returns200WithEntries() throws Exception {
        TimelineEntryResponse airway = new TimelineEntryResponse(
                TimelineEventType.AIRWAY,
                OffsetDateTime.of(2026, 6, 13, 9, 30, 0, 0, ZoneOffset.UTC),
                Map.of("id", "ffffffff-0000-0000-0000-000000000001", "eventType", "INTUBATION"));
        TimelineEntryResponse evaluation = new TimelineEntryResponse(
                TimelineEventType.EVALUATION,
                OffsetDateTime.of(2026, 6, 12, 22, 10, 0, 0, ZoneOffset.UTC),
                Map.of("id", "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
        when(service.getTimeline(PATIENT_ID)).thenReturn(List.of(airway, evaluation));

        mockMvc.perform(get(url()).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Línea de tiempo del paciente recuperada exitosamente"))
                .andExpect(jsonPath("$.data[0].type").value("AIRWAY"))
                .andExpect(jsonPath("$.data[0].payload.eventType").value("INTUBATION"))
                .andExpect(jsonPath("$.data[1].type").value("EVALUATION"));
    }

    @Test
    @DisplayName("returns 200 with an empty array for a patient with no events")
    void getTimeline_noEvents_returns200EmptyArray() throws Exception {
        when(service.getTimeline(PATIENT_ID)).thenReturn(List.of());

        mockMvc.perform(get(url()).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("returns 404 for an unknown patient")
    void getTimeline_unknownPatient_returns404() throws Exception {
        when(service.getTimeline(PATIENT_ID))
                .thenThrow(new BusinessLayerException("No se encontró el paciente con id: " + PATIENT_ID, NOT_FOUND));

        mockMvc.perform(get(url()).accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
