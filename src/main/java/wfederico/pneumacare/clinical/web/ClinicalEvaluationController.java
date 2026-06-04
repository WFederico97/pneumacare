package wfederico.pneumacare.clinical.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.clinical.application.ClinicalEvaluationService;
import wfederico.pneumacare.clinical.web.dto.CstatRequest;
import wfederico.pneumacare.clinical.web.dto.CstatResponse;
import wfederico.pneumacare.clinical.web.dto.PafiRequest;
import wfederico.pneumacare.clinical.web.dto.PafiResponse;
import wfederico.pneumacare.clinical.web.dto.RsbiRequest;
import wfederico.pneumacare.clinical.web.dto.RsbiResponse;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import org.slf4j.MDC;

/**
 * REST controller for respiratory clinical index calculations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/evaluations/rsbi}  — Rapid Shallow Breathing Index</li>
 *   <li>{@code POST /api/v1/evaluations/pafi}  — PaO₂/FiO₂ ratio (PaFi)</li>
 *   <li>{@code POST /api/v1/evaluations/cstat} — Static respiratory compliance (Cstat)</li>
 * </ul>
 *
 * <p>Spring MVC's {@code ServerHttpObservationFilter} automatically creates an
 * OTel HTTP server span for every request; the span includes the HTTP method,
 * URI template, and response status. The {@link ClinicalEvaluationService}
 * creates a child span for the calculation itself via {@code @Observed}.
 *
 * <p>Traces are exported via OTLP to the {@code grafana/otel-lgtm} collector
 * (Tempo). Metrics are pushed via Micrometer's OTLP registry to Mimir and
 * additionally scraped at {@code /actuator/prometheus}.
 */
@RestController
@RequestMapping("/api/v1/evaluations")
public class ClinicalEvaluationController {

    private final ClinicalEvaluationService evaluationService;

    public ClinicalEvaluationController(ClinicalEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * Calculates the Rapid Shallow Breathing Index.
     *
     * <p>Example request:
     * <pre>{@code
     * POST /api/v1/evaluations/rsbi
     * {
     *   "respiratoryRate": 22,
     *   "tidalVolume": 0.45
     * }
     * }</pre>
     */
    @PostMapping("/rsbi")
    public ResponseEntity<ApiResponseBase<RsbiResponse>> calculateRsbi(
            @Valid @RequestBody RsbiRequest request) {

        RsbiResponse result = evaluationService.calculateRsbi(request);

        return ResponseEntity.ok(
                ApiResponseBase.<RsbiResponse>builder()
                        .status(200)
                        .message(RequestMessageConstants.RSBI_CALCULATED)
                        .data(result)
                        .traceId(MDC.get("traceId"))
                        .build()
        );
    }

    /**
     * Calculates the PaO₂/FiO₂ ratio and classifies ARDS severity.
     *
     * <p>Example request:
     * <pre>{@code
     * POST /api/v1/evaluations/pafi
     * {
     *   "pao2": 80,
     *   "fio2": 0.40
     * }
     * }</pre>
     */
    @PostMapping("/pafi")
    public ResponseEntity<ApiResponseBase<PafiResponse>> calculatePafi(
            @Valid @RequestBody PafiRequest request) {

        PafiResponse result = evaluationService.calculatePafi(request);

        return ResponseEntity.ok(
                ApiResponseBase.<PafiResponse>builder()
                        .status(200)
                        .message(RequestMessageConstants.PAFI_CALCULATED)
                        .data(result)
                        .traceId(MDC.get("traceId"))
                        .build()
        );
    }
    /**
     * Calculates the static respiratory system compliance (Cstat).
     *
     * <p>Example request:
     * <pre>{@code
     * POST /api/v1/evaluations/cstat
     * {
     *   "tidalVolume": 500,
     *   "plateauPressure": 25,
     *   "peepTotal": 5
     * }
     * }</pre>
     */
    @PostMapping("/cstat")
    public ResponseEntity<ApiResponseBase<CstatResponse>> calculateCstat(@Valid @RequestBody CstatRequest request){
        CstatResponse result = evaluationService.calculateCstat(request);

        return ResponseEntity.ok(
                ApiResponseBase.<CstatResponse>builder()
                        .status(200)
                        .message(RequestMessageConstants.CSTAT_CALCULATED)
                        .data(result)
                        .traceId(MDC.get("traceId"))
                        .build()
        );
    }
}
