package wfederico.pneumacare.patient;

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
import wfederico.pneumacare.patient.application.IcuBedService;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.web.IcuBedsController;
import wfederico.pneumacare.patient.web.dto.IcuBedResponse;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = IcuBedsController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class IcuBedsControllerTest {

    private static final String URL = "/api/v1/icu-beds";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IcuBedService service;

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

    @Test
    @DisplayName("getIcuBeds_authenticatedUser_returns200WithList")
    @WithMockUser
    void getIcuBeds_authenticatedUser_returns200WithList() throws Exception {
        when(service.findBedsForAuthenticatedIcu()).thenReturn(List.of(
                new IcuBedResponse("BED-001", BedStatus.AVAILABLE),
                new IcuBedResponse("BED-002", BedStatus.OCCUPIED)
        ));

        mockMvc.perform(get(URL).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Camas de UCI recuperadas exitosamente"))
                .andExpect(jsonPath("$.data[0].bedNumber").value("BED-001"))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[1].bedNumber").value("BED-002"))
                .andExpect(jsonPath("$.data[1].status").value("OCCUPIED"));
    }

    @Test
    @DisplayName("getIcuBeds_authenticatedUserWithNoBeds_returns200WithEmptyArray")
    @WithMockUser
    void getIcuBeds_authenticatedUserWithNoBeds_returns200WithEmptyArray() throws Exception {
        when(service.findBedsForAuthenticatedIcu()).thenReturn(List.of());

        mockMvc.perform(get(URL).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getIcuBeds_noAuthenticationInDev_returns200")
    void getIcuBeds_noAuthenticationInDev_returns200() throws Exception {
        when(service.findBedsForAuthenticatedIcu()).thenReturn(List.of(
                new IcuBedResponse("BED-001", BedStatus.AVAILABLE)
        ));

        mockMvc.perform(get(URL).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].bedNumber").value("BED-001"));
    }
}
