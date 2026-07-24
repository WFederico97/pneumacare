package wfederico.pneumacare.analytics.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.analytics.application.AnalyticsService;
import wfederico.pneumacare.analytics.web.dto.AnalyticsSummaryResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

/** Read-only analytics summary, scoped to the caller's role by the service. */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PreAuthorize("hasRole('THERAPIST')")
    @GetMapping("/summary")
    public ApiResponseBase<AnalyticsSummaryResponse> summary(
            Authentication authentication,
            @RequestParam(name = "windowDays", defaultValue = "14") int windowDays) {
        return ApiResponseBase.<AnalyticsSummaryResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Resumen analítico")
                .data(analyticsService.summarize(authentication, windowDays))
                .traceId(MDC.get("traceId"))
                .build();
    }
}
