package wfederico.pneumacare.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Emits an RFC 7807 problem for CSRF and filter-level access denials.
 *
 * <p>Distinguishes the two cases the way {@code GlobalExceptionHandler} already
 * does for method-security denials: {@code 401} when the caller is anonymous
 * (they need to authenticate) and {@code 403} when they are authenticated but
 * lack the authority. Returning 403 for both made an unauthenticated request
 * look like a permissions problem.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;

        HttpStatus status = anonymous ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        String detail = anonymous ? "No autenticado" : "Acceso denegado";

        String uri = request.getRequestURI();
        if (uri != null && uri.matches("[A-Za-z0-9/_.\\-]*")) {
            log.warn("Access denied: anonymous={}, uri={}, reason={}",
                    anonymous, uri, ex.getClass().getSimpleName());
        } else {
            log.warn("Access denied: anonymous={}, uri=[unsafe], reason={}",
                    anonymous, ex.getClass().getSimpleName());
        }
        ProblemSupport.write(response, status, detail, request.getRequestURI(), objectMapper);
    }
}
