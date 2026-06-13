package wfederico.pneumacare.shift;

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
import wfederico.pneumacare.shift.application.MedicalShiftService;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.web.MedicalShiftController;
import wfederico.pneumacare.shift.web.dto.CreateShiftRequest;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = MedicalShiftController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class MedicalShiftControllerTest {

    private static final String URL = "/api/v1/shifts";
    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MedicalShiftService service;

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

    private static ShiftResponse openResponse() {
        return new ShiftResponse(SHIFT_ID, ICU_ID, CHIEF_ID, ShiftStatus.OPEN,
                OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    @Test
    @DisplayName("openShift_validRequest_returns201WithLocation (AC1)")
    void openShift_validRequest_returns201WithLocation() throws Exception {
        when(service.open(any(CreateShiftRequest.class))).thenReturn(openResponse());

        String body = """
                { "icuId": "%s" }
                """.formatted(ICU_ID);

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", URL + "/" + SHIFT_ID))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Turno abierto exitosamente"))
                .andExpect(jsonPath("$.data.id").value(SHIFT_ID.toString()))
                .andExpect(jsonPath("$.data.icuId").value(ICU_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("openShift_missingIcuId_returns400")
    void openShift_missingIcuId_returns400() throws Exception {
        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("openShift_duplicateOpen_returns409 (AC2)")
    void openShift_duplicateOpen_returns409() throws Exception {
        when(service.open(any(CreateShiftRequest.class)))
                .thenThrow(new BusinessLayerException("Ya existe un turno abierto para esta UCI", CONFLICT));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON)
                        .content("{ \"icuId\": \"" + ICU_ID + "\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("openShift_unknownIcu_returns422 (AC3)")
    void openShift_unknownIcu_returns422() throws Exception {
        when(service.open(any(CreateShiftRequest.class)))
                .thenThrow(new BusinessLayerException("No existe la UCI con id: " + ICU_ID, UNPROCESSABLE_ENTITY));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON)
                        .content("{ \"icuId\": \"" + ICU_ID + "\" }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("closeShift_validId_returns200 (AC4)")
    void closeShift_validId_returns200() throws Exception {
        ShiftResponse closed = new ShiftResponse(SHIFT_ID, ICU_ID, CHIEF_ID, ShiftStatus.CLOSED,
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC));
        when(service.close(SHIFT_ID)).thenReturn(closed);

        mockMvc.perform(patch(URL + "/" + SHIFT_ID + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Turno cerrado exitosamente"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.endTime").isNotEmpty());
    }

    @Test
    @DisplayName("closeShift_alreadyClosed_returns409 (AC5)")
    void closeShift_alreadyClosed_returns409() throws Exception {
        when(service.close(SHIFT_ID))
                .thenThrow(new BusinessLayerException("El turno ya está cerrado", CONFLICT));

        mockMvc.perform(patch(URL + "/" + SHIFT_ID + "/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("closeShift_unknownShift_returns404 (AC6)")
    void closeShift_unknownShift_returns404() throws Exception {
        when(service.close(SHIFT_ID))
                .thenThrow(new BusinessLayerException("No existe el turno con id: " + SHIFT_ID, NOT_FOUND));

        mockMvc.perform(patch(URL + "/" + SHIFT_ID + "/close"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
