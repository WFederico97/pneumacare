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
import wfederico.pneumacare.shift.application.ShiftHandoverService;
import wfederico.pneumacare.shift.web.ShiftHandoverController;
import wfederico.pneumacare.shift.web.dto.CreateHandoverRequest;
import wfederico.pneumacare.shift.web.dto.HandoverResponse;

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
        value = ShiftHandoverController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class ShiftHandoverControllerTest {

    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID AUTHOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID HANDOVER_ID = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/shifts/" + SHIFT_ID + "/handovers";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShiftHandoverService service;

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

    private static HandoverResponse handoverResponse() {
        return new HandoverResponse(HANDOVER_ID, SHIFT_ID, AUTHOR_ID, "Cama 3 estable",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static String body(String content) {
        return "{ \"notesContent\": \"" + content + "\" }";
    }

    @Test
    @DisplayName("create note on OPEN shift returns 201 with Location and body")
    void createHandover_valid_returns201WithLocation() throws Exception {
        when(service.create(eq(SHIFT_ID), any(CreateHandoverRequest.class))).thenReturn(handoverResponse());

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(body("Cama 3 estable")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", URL))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Nota de relevo registrada exitosamente"))
                .andExpect(jsonPath("$.data.id").value(HANDOVER_ID.toString()))
                .andExpect(jsonPath("$.data.shiftId").value(SHIFT_ID.toString()))
                .andExpect(jsonPath("$.data.authorId").value(AUTHOR_ID.toString()));
    }

    @Test
    @DisplayName("create note on CLOSED shift returns 409")
    void createHandover_closedShift_returns409() throws Exception {
        when(service.create(eq(SHIFT_ID), any(CreateHandoverRequest.class)))
                .thenThrow(new BusinessLayerException("No se pueden agregar notas a un turno cerrado", CONFLICT));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(body("note")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("create note on unknown shift returns 404")
    void createHandover_unknownShift_returns404() throws Exception {
        when(service.create(eq(SHIFT_ID), any(CreateHandoverRequest.class)))
                .thenThrow(new BusinessLayerException("No existe el turno con id: " + SHIFT_ID, NOT_FOUND));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(body("note")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("create note with empty content returns 422")
    void createHandover_emptyContent_returns422() throws Exception {
        when(service.create(eq(SHIFT_ID), any(CreateHandoverRequest.class)))
                .thenThrow(new BusinessLayerException("El contenido de la nota de relevo es obligatorio",
                        UNPROCESSABLE_CONTENT));

        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(body("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("list notes returns 200 with the notes")
    void getHandovers_returns200WithList() throws Exception {
        when(service.getForShift(SHIFT_ID)).thenReturn(List.of(handoverResponse()));

        mockMvc.perform(get(URL).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Notas de relevo recuperadas exitosamente"))
                .andExpect(jsonPath("$.data[0].id").value(HANDOVER_ID.toString()))
                .andExpect(jsonPath("$.data[0].notesContent").value("Cama 3 estable"));
    }

    @Test
    @DisplayName("list notes for unknown shift returns 404")
    void getHandovers_unknownShift_returns404() throws Exception {
        when(service.getForShift(SHIFT_ID))
                .thenThrow(new BusinessLayerException("No existe el turno con id: " + SHIFT_ID, NOT_FOUND));

        mockMvc.perform(get(URL).accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
