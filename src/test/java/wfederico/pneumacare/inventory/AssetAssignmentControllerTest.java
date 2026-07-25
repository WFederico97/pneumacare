package wfederico.pneumacare.inventory;

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
import wfederico.pneumacare.inventory.application.AssetAssignmentService;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.web.AssetAssignmentController;
import wfederico.pneumacare.inventory.web.dto.ActiveAssignmentResponse;
import wfederico.pneumacare.inventory.web.dto.AssetAssignmentResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AssetAssignmentController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class AssetAssignmentControllerTest {

    private static final String ASSIGN_URL = "/api/v1/assets/assign";
    private static final String UNASSIGN_URL = "/api/v1/assets/unassign";
    private static final UUID VENTILATOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetAssignmentService service;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUpMocks() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);
    }

    private static final String ASSIGN_BODY = """
            {
              "ventilatorId": "eeeeeeee-0000-0000-0000-000000000001",
              "patientId": "aaaaaaaa-0000-0000-0000-000000000001"
            }
            """;

    private static final String UNASSIGN_BODY = """
            {
              "ventilatorId": "eeeeeeee-0000-0000-0000-000000000001"
            }
            """;

    private AssetAssignmentResponse response(VentilatorStatus status, OffsetDateTime releasedAt) {
        return new AssetAssignmentResponse(UUID.randomUUID(), VENTILATOR_ID, PATIENT_ID,
                status, OffsetDateTime.now(), releasedAt);
    }

    @Test
    @DisplayName("assign returns 200 with IN_USE status")
    void assignReturns200() throws Exception {
        when(service.assign(any())).thenReturn(response(VentilatorStatus.IN_USE, null));

        mockMvc.perform(post(ASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("IN_USE"))
                .andExpect(jsonPath("$.data.ventilatorId").value(VENTILATOR_ID.toString()));
    }

    @Test
    @DisplayName("assign of a non-available ventilator returns 400")
    void assignBlockedReturns400() throws Exception {
        when(service.assign(any())).thenThrow(new BusinessLayerException(
                "El ventilador no está disponible (estado actual: MAINTENANCE)", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post(ASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("assign with unknown ventilator returns 404")
    void assignUnknownReturns404() throws Exception {
        when(service.assign(any())).thenThrow(new BusinessLayerException(
                "No se encontró el ventilador con id: " + VENTILATOR_ID, HttpStatus.NOT_FOUND));

        mockMvc.perform(post(ASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("assign when patient already assigned returns 409")
    void assignConflictReturns409() throws Exception {
        when(service.assign(any())).thenThrow(new BusinessLayerException(
                "El paciente ya tiene un ventilador asignado", HttpStatus.CONFLICT));

        mockMvc.perform(post(ASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASSIGN_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("assign with missing patientId returns 400")
    void assignMissingFieldReturns400() throws Exception {
        mockMvc.perform(post(ASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ventilatorId\": \"eeeeeeee-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("unassign returns 200 with AVAILABLE status")
    void unassignReturns200() throws Exception {
        when(service.unassign(any())).thenReturn(response(VentilatorStatus.AVAILABLE, OffsetDateTime.now()));

        mockMvc.perform(post(UNASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNASSIGN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("unassign with no active assignment returns 409")
    void unassignNoActiveReturns409() throws Exception {
        when(service.unassign(any())).thenThrow(new BusinessLayerException(
                "El ventilador no tiene una asignación activa", HttpStatus.CONFLICT));

        mockMvc.perform(post(UNASSIGN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNASSIGN_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("active returns 200 with the assignment when one exists")
    void activeReturnsAssignment() throws Exception {
        when(service.findActiveForPatient(PATIENT_ID))
                .thenReturn(new ActiveAssignmentResponse(VENTILATOR_ID, "SN-001", OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/assets/active").param("patientId", PATIENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serialNumber").value("SN-001"))
                .andExpect(jsonPath("$.data.ventilatorId").value(VENTILATOR_ID.toString()));
    }

    @Test
    @DisplayName("active returns 200 with null data when no ventilator is assigned")
    void activeReturnsNullWhenNone() throws Exception {
        when(service.findActiveForPatient(PATIENT_ID)).thenReturn(null);

        mockMvc.perform(get("/api/v1/assets/active").param("patientId", PATIENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
