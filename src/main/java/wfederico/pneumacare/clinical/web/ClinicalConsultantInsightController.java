package wfederico.pneumacare.clinical.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.clinical.application.ClinicalConsultantInsightService;
import wfederico.pneumacare.clinical.web.dto.InsightResponse;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.UUID;

/**
 * REST controller exposing cached clinical consultant guidance for an evaluation.
 *
 * <p>{@code GET /api/v1/evaluations/{id}/insights} returns the composed guidance,
 * computing and caching it on first read. THERAPIST-secured, matching the other
 * clinical evaluation endpoints. Shares the {@code /api/v1/evaluations} base path
 * with the evaluation controllers; the {@code /{id}/insights} sub-path is distinct.
 */
@Tag(name = "Evaluations", description = "Clinical consultant guidance for ventilator evaluations")
@Slf4j
@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class ClinicalConsultantInsightController {

    private final ClinicalConsultantInsightService service;

    @Operation(
            summary = "Get the clinical consultant insight for an evaluation",
            description = "Returns reference-grounded guidance for the evaluation's computed indices. "
                    + "The first call composes and caches the insight; later calls return the cached copy. "
                    + "Returns 404 if the evaluation does not exist. Required role (staging/prod): ROLE_THERAPIST.")
    @PreAuthorize("hasRole('THERAPIST')")
    @GetMapping("/{id}/insights")
    public ResponseEntity<ApiResponseBase<InsightResponse>> getInsight(@PathVariable UUID id) {
        log.debug("Consultant insight requested: evaluationId={}", id);

        InsightResponse data = service.getOrCreate(id);

        return ResponseEntity.ok(ApiResponseBase.<InsightResponse>builder()
                .status(HttpStatus.OK.value())
                .message(RequestMessageConstants.INSIGHT_RETRIEVED)
                .data(data)
                .traceId(MDC.get("traceId"))
                .build());
    }
}
