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
import wfederico.pneumacare.inventory.application.VentilatorService;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.web.VentilatorController;
import wfederico.pneumacare.inventory.web.dto.VentilatorResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;
import wfederico.pneumacare.shared.web.dto.PageResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link VentilatorController}: status codes,
 * ApiResponseBase envelope, bean validation, and error mapping.
 * Role enforcement is via @PreAuthorize and not active under the dev chain.
 */
@WebMvcTest(
        value = VentilatorController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class VentilatorControllerTest {

    private static final String URL = "/api/v1/ventilators";
    private static final UUID VENTILATOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VentilatorService service;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUpMocks() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);
    }

    private static final String VALID_BODY = """
            {
              "serialNumber": "SN-001",
              "brand": "TECME",
              "modelName": "GraphNet TS+",
              "icuId": "cccccccc-0000-0000-0000-000000000001"
            }
            """;

    private VentilatorResponse response(VentilatorStatus status) {
        return new VentilatorResponse(VENTILATOR_ID, "SN-001", "TECME", "GraphNet TS+",
                ICU_ID, status, OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    @DisplayName("POST with valid payload returns 201 and the persisted resource")
    void postValidReturns201() throws Exception {
        when(service.create(any())).thenReturn(response(VentilatorStatus.AVAILABLE));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.serialNumber").value("SN-001"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("POST with duplicate serial returns 409")
    void postDuplicateReturns409() throws Exception {
        when(service.create(any())).thenThrow(
                new BusinessLayerException("Ya existe un ventilador con ese número de serie",
                        HttpStatus.CONFLICT));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST without mandatory fields returns 400")
    void postMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\": \"SN-001\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST with an unknown brand value returns 400")
    void postInvalidBrandReturns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("TECME", "DRAEGER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET returns 200 with a paginated envelope")
    void getListReturns200() throws Exception {
        when(service.list(any(), eq(null))).thenReturn(new PageResponse<>(
                List.of(response(VentilatorStatus.AVAILABLE)), 0, 10, 1, 1));

        mockMvc.perform(get(URL).param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].serialNumber").value("SN-001"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /{id} returns 200 with the resource")
    void getByIdReturns200() throws Exception {
        when(service.getById(VENTILATOR_ID)).thenReturn(response(VentilatorStatus.AVAILABLE));

        mockMvc.perform(get(URL + "/" + VENTILATOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(VENTILATOR_ID.toString()));
    }

    @Test
    @DisplayName("GET /{id} for an unknown id returns 404")
    void getByIdReturns404() throws Exception {
        when(service.getById(VENTILATOR_ID)).thenThrow(
                new BusinessLayerException("No se encontró el ventilador con id: " + VENTILATOR_ID,
                        HttpStatus.NOT_FOUND));

        mockMvc.perform(get(URL + "/" + VENTILATOR_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH with a valid status returns 200 with the updated resource")
    void patchStatusReturns200() throws Exception {
        when(service.updateStatus(eq(VENTILATOR_ID), any()))
                .thenReturn(response(VentilatorStatus.MAINTENANCE));

        mockMvc.perform(patch(URL + "/" + VENTILATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MAINTENANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAINTENANCE"));
    }

    @Test
    @DisplayName("PATCH with an invalid status value returns 400")
    void patchInvalidStatusReturns400() throws Exception {
        mockMvc.perform(patch(URL + "/" + VENTILATOR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"BROKEN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE returns 204 on success")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(URL + "/" + VENTILATOR_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE of a ventilator with clinical history returns 409")
    void deleteReferencedReturns409() throws Exception {
        doThrow(new BusinessLayerException("El ventilador tiene historial clínico asociado",
                HttpStatus.CONFLICT))
                .when(service).delete(VENTILATOR_ID);

        mockMvc.perform(delete(URL + "/" + VENTILATOR_ID))
                .andExpect(status().isConflict());
    }
}
