package wfederico.pneumacare.analytics;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.analytics.application.ExecutiveAnalyticsService;
import wfederico.pneumacare.analytics.web.ExecutiveDashboardController;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.shared.security.SecurityConfig;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = ExecutiveDashboardController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        })
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class ExecutiveDashboardControllerTest {

    private static final String URL = "/api/v1/analytics/dashboard";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutiveAnalyticsService service;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stub() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
        when(service.dashboard()).thenReturn(new ExecutiveDashboardResponse(
                50.0, 3L, 2L,
                new ExecutiveDashboardResponse.AssetUtilization(3L, 5L, 2L, 30.0),
                4.5, 4.5, 0.5,
                new ExecutiveDashboardResponse.MortalityStats(2L, 1L, 0L, 50.0, 1L, 1L, 100.0),
                new ExecutiveDashboardResponse.ReadmissionStats(0L, 1L, 0.0, 50.0)));
    }

    @Test
    @DisplayName("returns 200 with the three metrics for the dev admin principal")
    void returnsPayload() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupancyRatePercent").value(50.0))
                .andExpect(jsonPath("$.data.alertFrequencyLast7Days").value(3))
                .andExpect(jsonPath("$.data.equipmentInMaintenanceCount").value(2));
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    @DisplayName("a DIRECTOR receives 200")
    void directorAllowed() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("an ADMIN receives 200")
    void adminAllowed() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "THERAPIST")
    @DisplayName("a THERAPIST is forbidden with 403")
    void therapistForbidden() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }
}
