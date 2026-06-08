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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.patient.application.PatientIdentifierTypeService;
import wfederico.pneumacare.patient.web.IdentifierTypeController;
import wfederico.pneumacare.patient.web.dto.IdentifierTypeResponse;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer unit tests for {@link IdentifierTypeController}.
 *
 * <p>Uses {@code @WebMvcTest} — no Spring Data, no Testcontainers, no Docker.
 * {@link PatientIdentifierTypeService} is mocked with {@code @MockitoBean}.
 * {@link StringRedisTemplate} is mocked to satisfy the {@code SecurityConfig}
 * constructor without a live Redis connection.
 *
 * <p>The {@code dev} profile activates {@code devSecurityFilterChain} which
 * grants {@code permitAll()} to {@code /api/**}, so no auth token is needed.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>Full catalog (6 types) returned with correct envelope fields</li>
 *   <li>Empty catalog returns HTTP 200 with an empty array (not 404)</li>
 * </ul>
 */
@WebMvcTest(
        value = IdentifierTypeController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class IdentifierTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientIdentifierTypeService service;

    /** Satisfies SecurityConfig constructor — no live Redis needed. */
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static final List<IdentifierTypeResponse> FULL_CATALOG = List.of(
            new IdentifierTypeResponse(1, "DNI",       "Documento Nacional de Identidad"),
            new IdentifierTypeResponse(2, "CUIL",      "Código Único de Identificación Laboral"),
            new IdentifierTypeResponse(3, "CUIT",      "Código Único de Identificación Tributaria"),
            new IdentifierTypeResponse(4, "LE",        "Libreta de Enrolamiento"),
            new IdentifierTypeResponse(5, "LC",        "Libreta Cívica"),
            new IdentifierTypeResponse(6, "Pasaporte", "Pasaporte")
    );

    // -------------------------------------------------------------------------
    // GET /api/v1/identifier-types
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Full catalog — returns 200 with all 6 identifier types in response envelope")
    void listIdentifierTypes_fullCatalog_returns200WithAllTypes() throws Exception {
        when(service.findAll()).thenReturn(FULL_CATALOG);

        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Identifier types retrieved successfully"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("DNI"))
                .andExpect(jsonPath("$.data[0].description").value("Documento Nacional de Identidad"))
                .andExpect(jsonPath("$.data[5].id").value(6))
                .andExpect(jsonPath("$.data[5].name").value("Pasaporte"));
    }

    @Test
    @DisplayName("Empty catalog — returns 200 with empty data array (not 404)")
    void listIdentifierTypes_emptyCatalog_returns200WithEmptyArray() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/identifier-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
