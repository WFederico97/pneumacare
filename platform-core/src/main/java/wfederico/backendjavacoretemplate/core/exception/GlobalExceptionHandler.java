package wfederico.backendjavacoretemplate.core.exception;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import wfederico.backendjavacoretemplate.core.web.ApiResponseBase;
import wfederico.backendjavacoretemplate.domain.exception.BusinessLayerException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseBase<Void>> handleGenericException(Exception ex) {
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(500)
                .message(String.format("API Error: %s", ex.getMessage()))
                .traceId(MDC.get("traceId"))
                .build();
        return ResponseEntity.status(500).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseBase<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));

        ApiResponseBase<Map<String, String>> response = ApiResponseBase.<Map<String, String>>builder()
                .status(400)
                .message("Validation error")
                .data(errors)
                .traceId(MDC.get("traceId"))
                .build();
        return ResponseEntity.status(400).body(response);
    }

    @ExceptionHandler(BusinessLayerException.class)
    public ResponseEntity<ApiResponseBase<Void>> handleBusinessExceptions(BusinessLayerException ex) {
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(ex.getStatusCode().value())
                .message(ex.getMessage())
                .traceId(MDC.get("traceId"))
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
}

