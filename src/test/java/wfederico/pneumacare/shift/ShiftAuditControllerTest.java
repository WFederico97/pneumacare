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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.shared.security.SecurityConfig;
import wfederico.pneumacare.shift.application.ShiftAuditService;
import wfederico.pneumacare.shift.web.ShiftAuditController;
import wfederico.pneumacare.shift.web.dto.AuditRevisionResponse;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ShiftAuditController} (PNMC-134): the {@code SCOPE_audit}
 * authorization gate on the audit history endpoints.
 *
 * <p>{@code @PreAuthorize} is active because {@link SecurityConfig} carries
 * {@code @EnableMethodSecurity}. A caller without {@code SCOPE_audit} is denied even
 * though the dev filter chain grants {@code permitAll()} to {@code /api/**}.
 */
@WebMvcTest(
        value = ShiftAuditController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class ShiftAuditControllerTest {

    private static final UUID SHIFT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final String SHIFT_AUDIT_URL = "/api/v1/shifts/" + SHIFT_ID + "/audit";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShiftAuditService service;

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

    private static AuditRevisionResponse<ShiftResponse> sampleRevision() {
        ShiftResponse snapshot = new ShiftResponse(SHIFT_ID, UUID.randomUUID(), ACTOR_ID,
                wfederico.pneumacare.shift.domain.ShiftStatus.CLOSED,
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC));
        return new AuditRevisionResponse<>(2L, "UPDATE", ACTOR_ID,
                OffsetDateTime.now(ZoneOffset.UTC), snapshot);
    }

    @Test
    @DisplayName("getShiftAudit with COMPLIANCE returns 200 and the revision history")
    @WithMockUser(roles = "COMPLIANCE")
    void shiftAudit_withComplianceRole_returns200() throws Exception {
        when(service.getShiftHistory(SHIFT_ID)).thenReturn(List.of(sampleRevision()));

        mockMvc.perform(get(SHIFT_AUDIT_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].revisionType").value("UPDATE"))
                .andExpect(jsonPath("$.data[0].actorId").value(ACTOR_ID.toString()));
    }

    @Test
    @DisplayName("getShiftAudit without the compliance role returns 403 (problem+json)")
    @WithMockUser(roles = "THERAPIST")
    void shiftAudit_withoutComplianceRole_returns403() throws Exception {
        mockMvc.perform(get(SHIFT_AUDIT_URL))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("getShiftAudit while anonymous returns 401 (problem+json)")
    @WithAnonymousUser
    void shiftAudit_anonymous_returns401() throws Exception {
        mockMvc.perform(get(SHIFT_AUDIT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }
}
