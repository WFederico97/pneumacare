package wfederico.pneumacare.clinical.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.clinical.application.ClinicalEvaluationService;
import wfederico.pneumacare.clinical.web.dto.PafiRequest;
import wfederico.pneumacare.clinical.web.dto.PafiResponse;
import wfederico.pneumacare.clinical.web.dto.RsbiRequest;
import wfederico.pneumacare.clinical.web.dto.RsbiResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import org.slf4j.MDC;

/**
 * REST controller for respiratory clinical index calculations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/evaluations/rsbi} — Rapid Shallow Breathing Index</li>
 *   <li>{@code POST /api/evaluations/pafi} — PaO₂/FiO₂ ratio (PaFi)</li>
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
@RequestMapping("/api/evaluations")
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
     * POST /api/evaluations/rsbi
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
                        .message("RSBI calculated successfully")
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
     * POST /api/evaluations/pafi
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
                        .message("PaFi calculated successfully")
                        .data(result)
                        .traceId(MDC.get("traceId"))
                        .build()
        );
    }
}
