package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemHandlersTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessDeniedHandler_authenticatedButUnauthorised_writes403ProblemJson() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "jdoe", null, List.of(new SimpleGrantedAuthority("ROLE_THERAPIST"))));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/shifts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemDetailAccessDeniedHandler(objectMapper)
                .handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"title\":\"Forbidden\"")
                .contains("\"instance\":\"/api/v1/shifts\"")
                .doesNotContain("AccessDeniedException");
    }

    /**
     * An anonymous caller needs to authenticate, not different permissions —
     * reporting 403 for both made a missing session look like a role problem.
     */
    @Test
    void accessDeniedHandler_anonymous_writes401ProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/shifts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemDetailAccessDeniedHandler(objectMapper)
                .handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"title\":\"Unauthorized\"")
                .doesNotContain("AccessDeniedException");
    }

    @Test
    void authenticationEntryPoint_writes401ProblemJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/patients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemDetailAuthenticationEntryPoint(objectMapper)
                .commence(request, response, new InsufficientAuthenticationException("anon"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"title\":\"Unauthorized\"");
    }
}
