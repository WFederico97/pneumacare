package wfederico.pneumacare.analytics.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.analytics.application.HierarchyAnalyticsService;
import wfederico.pneumacare.analytics.application.HierarchyLevel;
import wfederico.pneumacare.analytics.web.dto.HierarchyAnalyticsResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Multi-level analytics rollup endpoint. Aggregates ICU metrics by province,
 * institution, or patient. Access per level is enforced by the service; the
 * organizational levels are director/admin only.
 */
@Tag(name = "Hierarchy analytics", description = "Multi-level province/institution/patient rollups")
@RestController
@RequestMapping("/api/v1/analytics/hierarchy")
public class HierarchyAnalyticsController {

    private final HierarchyAnalyticsService service;

    public HierarchyAnalyticsController(HierarchyAnalyticsService service) {
        this.service = service;
    }

    @Operation(summary = "Aggregated ICU metrics for a hierarchy level")
    @PreAuthorize("hasAnyRole('THERAPIST','CHIEF_OF_GUARD','DIRECTOR','ADMIN')")
    @GetMapping
    public ApiResponseBase<HierarchyAnalyticsResponse> aggregate(
            Authentication authentication,
            @RequestParam(name = "level", defaultValue = "PATIENT") HierarchyLevel level,
            @RequestParam(name = "windowDays", defaultValue = "14") int windowDays) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return ApiResponseBase.<HierarchyAnalyticsResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Agregación jerárquica")
                .data(service.aggregate(level, windowDays, roles))
                .traceId(MDC.get("traceId"))
                .build();
    }
}
