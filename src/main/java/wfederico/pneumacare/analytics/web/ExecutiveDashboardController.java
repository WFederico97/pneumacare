package wfederico.pneumacare.analytics.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.analytics.application.ExecutiveAnalyticsService;
import wfederico.pneumacare.analytics.web.dto.ExecutiveDashboardResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

/**
 * Executive analytics dashboard for the Hospital Director.
 *
 * <p>{@code GET /api/v1/analytics/dashboard} returns aggregated ICU metrics
 * (occupancy %, 7-day alert frequency, equipment in maintenance). Restricted to
 * {@code ROLE_DIRECTOR} or {@code ROLE_ADMIN}; aggregate-only, no patient records.
 */
@Tag(name = "Analytics", description = "Executive analytics aggregation")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class ExecutiveDashboardController {

    private final ExecutiveAnalyticsService service;

    @Operation(
            summary = "Executive ICU metrics dashboard",
            description = "Aggregated bed occupancy %, alert frequency over the last 7 days, "
                    + "and ventilators in maintenance. Required role: ROLE_DIRECTOR or ROLE_ADMIN.")
    @PreAuthorize("hasAnyRole('DIRECTOR','ADMIN')")
    @GetMapping("/dashboard")
    public ApiResponseBase<ExecutiveDashboardResponse> dashboard() {
        return ApiResponseBase.<ExecutiveDashboardResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Panel ejecutivo recuperado")
                .data(service.dashboard())
                .traceId(MDC.get("traceId"))
                .build();
    }
}
