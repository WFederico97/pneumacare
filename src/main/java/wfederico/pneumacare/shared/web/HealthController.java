package wfederico.pneumacare.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;

@Tag(name = "Health", description = "Connectivity and service health check")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(summary = "Health check", description = "Returns the current health status of the service. Useful for frontend-backend connectivity verification.")
    @GetMapping
    public ResponseEntity<ApiResponseBase<HealthStatusResponse>> health() {
        ApiResponseBase<HealthStatusResponse> response = ApiResponseBase.<HealthStatusResponse>builder()
                .status(200)
                .message(RequestMessageConstants.HEALTH_OK)
                .data(HealthStatusResponse.up())
                .traceId(MDC.get("traceId"))
                .build();
        return ResponseEntity.ok(response);
    }
}
