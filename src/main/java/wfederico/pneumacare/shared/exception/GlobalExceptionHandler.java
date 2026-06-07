package wfederico.pneumacare.shared.exception;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
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
