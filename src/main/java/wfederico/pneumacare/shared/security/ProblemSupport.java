package wfederico.pneumacare.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds RFC 7807 ({@code application/problem+json}) bodies for auth failures.
 * The body never includes class, method, or SpEL detail.
 */
public final class ProblemSupport {

    public static final String CONTENT_TYPE = "application/problem+json";

    private ProblemSupport() {
    }

    public static Map<String, Object> body(HttpStatus status, String detail, String instance) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        if (instance != null) {
            problem.put("instance", instance);
        }
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.put("traceId", traceId);
        }
        return problem;
    }

    /**
     * Neutralizes CR/LF so user-controlled values (e.g. the request URI) cannot
     * forge or split log records (log injection).
     */
    public static String sanitizeForLog(String value) {
        return value == null ? null : value.replaceAll("[\r\n]", "_");
    }

    public static void write(HttpServletResponse response, HttpStatus status, String detail,
                             String instance, ObjectMapper objectMapper) throws IOException {
        response.setStatus(status.value());
        response.setContentType(CONTENT_TYPE);
        response.getWriter().write(objectMapper.writeValueAsString(body(status, detail, instance)));
    }
}
