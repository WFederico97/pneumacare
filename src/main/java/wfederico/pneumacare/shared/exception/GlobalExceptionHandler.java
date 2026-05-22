package wfederico.pneumacare.shared.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import wfederico.pneumacare.shared.web.ApiResponseBase;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseBase<Void>> handleGenericException(Exception ex) {
        String traceId = MDC.get("traceId");
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(500)
                .message(String.format("API Error: %s", ex.getMessage()))
                .traceId(traceId)
                .build();
        return ResponseEntity.status(500).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseBase<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
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
        String traceId = MDC.get("traceId");
        ApiResponseBase<Void> response = ApiResponseBase.<Void>builder()
                .status(ex.getStatusCode().value())
                .message(ex.getMessage())
                .traceId(traceId)
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
}
