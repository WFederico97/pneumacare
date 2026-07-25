package wfederico.pneumacare.analytics;

import org.junit.jupiter.api.BeforeEach;
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
import wfederico.pneumacare.analytics.application.AnalyticsService;
import wfederico.pneumacare.analytics.web.AnalyticsController;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.shared.security.SecurityConfig;
import wfederico.pneumacare.shared.security.user.UserRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AnalyticsController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        })
@Import({SecurityConfig.class, AnalyticsService.class})
@ActiveProfiles("dev")
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StringRedisTemplate redisTemplate;
    @MockitoBean private IcuBedRepository beds;
    @MockitoBean private EvaluationRepository evaluations;
    @MockitoBean private SbtRepository sbts;
    @MockitoBean private MedicalShiftRepository shifts;
    @MockitoBean private PatientRepository patients;
    @MockitoBean private UserRepository users;
    @MockitoBean private AirwayEventRepository airwayEvents;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stub() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
        when(beds.countByStatus(any(BedStatus.class))).thenReturn(2L);
        when(sbts.countByToleranceResultAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(evaluations.countByRsbiInterpretationAndEvaluationTimeAfter(any(), any())).thenReturn(0L);
        when(evaluations.countDailySince(any())).thenReturn(List.of());
        when(shifts.existsByStatus(any())).thenReturn(true);
        when(shifts.countByStartTimeAfter(any())).thenReturn(3L);
        when(patients.countByAdmissionDateAfter(any())).thenReturn(1L);
        when(users.count()).thenReturn(4L);
        when(users.countByEnabled(anyBoolean())).thenReturn(4L);
        when(users.countByRole()).thenReturn(List.of());
        when(patients.findIdsByRespiratoryStatus(any())).thenReturn(List.of());
        when(sbts.countAttemptsByPatientSince(any())).thenReturn(List.of());
        when(airwayEvents.findAllByOrderByPatientIdAscEventTimeAsc()).thenReturn(List.of());
    }

    @Test
    void summary_asAdmin_returnsAllSections() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinical").exists())
                .andExpect(jsonPath("$.data.ward").exists())
                .andExpect(jsonPath("$.data.iam.totalUsers").value(4));
    }
}
