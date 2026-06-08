package wfederico.pneumacare.shared.exception;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import wfederico.pneumacare.shared.web.ApiResponseBase;

/**
 * Global exception handler for all REST controllers.
 *
 * <h2>PII safety</h2>
 * <ul>
 *   <li>Unexpected exceptions ({@link Exception}) are logged at ERROR level with
 *       their full stack trace so the on-call engineer can diagnose the issue.
 *       The HTTP response body returns only a generic Spanish message —
 *       raw exception messages are never forwarded to the client, preventing
 *       internal details (column names, stack frames, etc.) from leaking.</li>
 *   <li>{@link BusinessLayerException} messages are crafted from
 *       {@code ExceptionMessageConstants} which contain no PII, so they are safe
 *       to return verbatim.</li>
 *   <li>Validation field errors contain only field names and constraint messages —
 *       never the submitted values.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String GENERIC_SERVER_ERROR = "Error interno del servidor";

    /**
     * Handles {@link AccessDeniedException} thrown by {@code @PreAuthorize} AOP proxies.
     *
     * <p>Returns 401 when the requester is anonymous (no authentication token or
     * {@link AnonymousAuthenticationToken}), and 403 when the requester is authenticated
     * but lacks the required role/authority.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseBase<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAnonymous = auth == null || auth instanceof AnonymousAuthenticationToken;

        int status = isAnonymous ? 401 : 403;
        String message = isAnonymous ? "No autenticado" : "Acceso denegado";

        log.warn("Access denied: anonymous={}, reason={}", isAnonymous, ex.getMessage());

        String traceId = MDC.get("traceId");
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(status)
                .message(message)
                .traceId(traceId)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseBase<Void>> handleGenericException(Exception ex) {
        // Log the full exception internally so engineers can diagnose it.
        // Never expose ex.getMessage() in the response — it may contain
        // internal details or, in the case of JPA constraint violations,
        // column values that could be PII.
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        String traceId = MDC.get("traceId");
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(500)
                .message(GENERIC_SERVER_ERROR)
                .traceId(traceId)
                .build();
        return ResponseEntity.status(500).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseBase<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        String traceId = MDC.get("traceId");
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(
                fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );

        ApiResponseBase<Map<String, String>> response = ApiResponseBase.<Map<String, String>>builder()
                .status(400)
                .message("API Error")
                .data(errors)
                .traceId(traceId)
                .build();
        return ResponseEntity.status(400).body(response);
    }

    /**
     * Handles malformed request bodies (invalid JSON syntax, unknown enum values,
     * type-mismatched fields).
     *
     * <p>The exception's raw message often quotes the offending value, which may be
     * PII or otherwise sensitive — never forward it to the client. The response
     * carries a generic Spanish message; the underlying cause is logged at WARN
     * for diagnostics.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseBase<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {

        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getClass().getSimpleName());

        String traceId = MDC.get("traceId");
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(400)
                .message("Cuerpo de la solicitud inválido")
                .traceId(traceId)
                .build();
        return ResponseEntity.status(400).body(response);
    }

    @ExceptionHandler(BusinessLayerException.class)
    public ResponseEntity<ApiResponseBase<Void>> handleBusinessExceptions(BusinessLayerException ex) {
        log.warn("Business error: status={}, message={}", ex.getStatusCode().value(), ex.getMessage());

        String traceId = MDC.get("traceId");
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(ex.getStatusCode().value())
                .message(ex.getMessage())
                .traceId(traceId)
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
}
