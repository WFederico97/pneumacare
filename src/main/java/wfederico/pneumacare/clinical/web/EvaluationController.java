package wfederico.pneumacare.clinical.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.clinical.application.EvaluationPersistenceService;
import wfederico.pneumacare.clinical.web.dto.CreateEvaluationRequest;
import wfederico.pneumacare.clinical.web.dto.EvaluationResponse;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

/**
 * REST controller for evaluation persistence.
 *
 * <p>Handles {@code POST /api/v1/evaluations}: receives raw ventilator parameters,
 * delegates calculation and persistence to {@link EvaluationPersistenceService},
 * and returns the immutable evaluation record with computed clinical index snapshots.
 *
 * <h2>Security</h2>
 * Access is restricted to authenticated users with {@code ROLE_THERAPIST}
 * via {@code @PreAuthorize}. In {@code staging}/{@code prod} the role is derived
 * from the OAuth2 JWT claims. In the {@code dev} profile no JWT is required by the
 * filter chain, but the {@code @PreAuthorize} check still fires — use
 * {@code @WithMockUser(roles = "THERAPIST")} in tests.
 *
 * <p>This controller intentionally uses the same base path
 * ({@code /api/v1/evaluations}) as {@link ClinicalEvaluationController} but
 * maps only the root {@code POST} (no sub-path). Spring MVC routes the sub-path
 * variants ({@code /rsbi}, {@code /pafi}, {@code /cstat}) to the other controller.
 */
@Tag(name = "Evaluations", description = "Ventilator evaluation persistence — stores readings and computed clinical indices")
@Slf4j
@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationPersistenceService service;

    @Operation(
            summary = "Persist a ventilator evaluation",
            description = """
                    Receives raw ventilator parameters, calculates RSBI, PaFi, and Cstat
                    server-side, and stores the immutable evaluation record.

                    **Calculation conventions:**
                    - RSBI = f / (Vt[mL] / 1000) — breaths·min⁻¹·L⁻¹
                    - PaFi = PaO₂ / FiO₂ — mmHg
                    - Cstat = Vt[mL] / (Pplat − PEEP) — mL/cmH₂O

                    `created_by` is automatically populated from the JWT `sub` claim.

                    **Required role (staging/prod):** `ROLE_THERAPIST` — open in dev.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    name = "Minimal evaluation",
                                    summary = "DNI-only patient with standard TECME readings",
                                    value = """
                                            {
                                              "patientId":           "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                                              "shiftId":             "bbbbbbbb-0000-0000-0000-000000000001",
                                              "physicalVentilatorId":"cccccccc-0000-0000-0000-000000000001",
                                              "f":    15,
                                              "vt":   500,
                                              "pao2": 85,
                                              "fio2": 0.40,
                                              "pplat": 25,
                                              "peep":   5
                                            }
                                            """))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Evaluation persisted. Response includes calculated RSBI, PaFi, and Cstat snapshots.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "status": 201,
                                              "message": "Evaluación registrada exitosamente",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "data": {
                                                "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                                                "patientId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                                                "shiftId": "bbbbbbbb-0000-0000-0000-000000000001",
                                                "physicalVentilatorId": "cccccccc-0000-0000-0000-000000000001",
                                                "evaluationTime": "2026-06-06T10:00:00-03:00",
                                                "f": 15,
                                                "vt": 500,
                                                "pao2": 85,
                                                "fio2": 0.40,
                                                "pplat": 25,
                                                "peep": 5,
                                                "rsbiSnapshot": 30.00,
                                                "rsbiInterpretation": "FAVORABLE",
                                                "pafiSnapshot": 212.50,
                                                "pafiClassification": "MILD_ARDS",
                                                "cstatSnapshot": 25.00,
                                                "cstatInterpretation": "LOW",
                                                "alertTriggered": false,
                                                "createdBy": "550e8400-e29b-41d4-a716-446655440000"
                                              }
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure — required field missing, value out of range, " +
                            "or pplat ≤ peep.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(
                    responseCode = "401",
                    description = "No valid authentication. Provide a Bearer token with ROLE_THERAPIST " +
                            "(staging/prod only).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PreAuthorize("hasRole('THERAPIST')")
    @PostMapping
    public ResponseEntity<ApiResponseBase<EvaluationResponse>> createEvaluation(
            @Valid @RequestBody CreateEvaluationRequest request) {

        log.debug("Evaluation admission requested: patientId={}, shiftId={}, ventilatorId={}",
                request.patientId(), request.shiftId(), request.physicalVentilatorId());

        EvaluationResponse data = service.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseBase.<EvaluationResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message(RequestMessageConstants.EVALUATION_PERSISTED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
