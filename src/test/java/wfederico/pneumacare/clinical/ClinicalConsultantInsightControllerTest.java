package wfederico.pneumacare.clinical;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.clinical.application.ClinicalConsultantInsightService;
import wfederico.pneumacare.clinical.web.ClinicalConsultantInsightController;
import wfederico.pneumacare.clinical.web.dto.InsightResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = ClinicalConsultantInsightController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class ClinicalConsultantInsightControllerTest {

    private static final UUID EVAL_ID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final String URL = "/api/v1/evaluations/" + EVAL_ID + "/insights";
    private static final String TEXT = "RSBI above 105 predicts weaning failure. Ref: Yang & Tobin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClinicalConsultantInsightService service;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpMocks() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);
    }

    @Test
    @DisplayName("cache miss returns 200 with cached=false and the text")
    void missReturns200() throws Exception {
        when(service.getOrCreate(eq(EVAL_ID)))
                .thenReturn(new InsightResponse(EVAL_ID, TEXT, false));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.evaluationId").value(EVAL_ID.toString()))
                .andExpect(jsonPath("$.data.insightText").value(TEXT))
                .andExpect(jsonPath("$.data.cached").value(false));
    }

    @Test
    @DisplayName("cache hit returns 200 with cached=true")
    void hitReturns200Cached() throws Exception {
        when(service.getOrCreate(eq(EVAL_ID)))
                .thenReturn(new InsightResponse(EVAL_ID, TEXT, true));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(true));
    }

    @Test
    @DisplayName("unknown evaluation id returns 404")
    void unknownReturns404() throws Exception {
        when(service.getOrCreate(eq(EVAL_ID))).thenThrow(new BusinessLayerException(
                "No se encontró la evaluación con id: " + EVAL_ID, HttpStatus.NOT_FOUND));

        mockMvc.perform(get(URL))
                .andExpect(status().isNotFound());
    }
}
