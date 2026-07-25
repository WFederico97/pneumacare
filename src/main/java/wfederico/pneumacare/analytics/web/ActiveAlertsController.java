package wfederico.pneumacare.analytics.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.analytics.application.ActiveAlertsService;
import wfederico.pneumacare.analytics.web.dto.ActiveAlertResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.List;

/**
 * Lists the currently-active clinical alerts across the ICU for the Alertas view.
 * Available to any clinical role; aggregate/triage data only (no patient PII).
 */
@Tag(name = "Alerts", description = "Active clinical alerts")
@RestController
@RequestMapping("/api/v1/alerts")
public class ActiveAlertsController {

    private final ActiveAlertsService service;

    public ActiveAlertsController(ActiveAlertsService service) {
        this.service = service;
    }

    @Operation(summary = "List active clinical alerts (latest evaluation tripped a threshold)")
    @PreAuthorize("hasAnyRole('THERAPIST','CHIEF_OF_GUARD','DIRECTOR','ADMIN')")
    @GetMapping
    public ApiResponseBase<List<ActiveAlertResponse>> activeAlerts() {
        return ApiResponseBase.<List<ActiveAlertResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Alertas clínicas activas")
                .data(service.activeAlerts())
                .traceId(MDC.get("traceId"))
                .build();
    }
}
