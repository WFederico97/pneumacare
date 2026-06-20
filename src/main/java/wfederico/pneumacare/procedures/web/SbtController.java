package wfederico.pneumacare.procedures.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.procedures.application.SbtService;
import wfederico.pneumacare.procedures.web.dto.CreateSbtRequest;
import wfederico.pneumacare.procedures.web.dto.SbtResponse;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Spontaneous Breathing Trials — PNMC-95.
 *
 * <p>Authorization ({@code ROLE_THERAPIST} / {@code ROLE_CHIEF_OF_GUARD}) is
 * intentionally not enforced yet: authentication/login is a separate backlog
 * effort (next sprint). In dev all {@code /api/**} endpoints are open, consistent
 * with the other controllers.
 */
@Tag(name = "SBT", description = "Spontaneous Breathing Trial recording and history")
@Slf4j
@RestController
@RequestMapping("/api/v1/procedures/sbt")
@RequiredArgsConstructor
public class SbtController {

    private final SbtService service;

    @Operation(
            summary = "Record an SBT result",
            description = "Records the outcome of a spontaneous breathing trial for a patient, "
                    + "linked to the patient's OPEN shift.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "SBT recorded."),
            @ApiResponse(responseCode = "400", description = "Missing or malformed body."),
            @ApiResponse(responseCode = "404", description = "The referenced patient does not exist."),
            @ApiResponse(responseCode = "409", description = "No OPEN shift for the patient's ICU."),
            @ApiResponse(responseCode = "422", description = "durationMinutes is not a positive integer.")
    })
    @PostMapping
    public ResponseEntity<ApiResponseBase<SbtResponse>> recordSbt(
            @Valid @RequestBody CreateSbtRequest request) {
        SbtResponse data = service.register(request);

        URI location = URI.create("/api/v1/procedures/sbt?patientId=" + data.patientId());
        return ResponseEntity
                .created(location)
                .body(ApiResponseBase.<SbtResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message(RequestMessageConstants.SBT_REGISTERED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @Operation(
            summary = "List a patient's SBT history",
            description = "Returns the patient's SBT records ordered by recorded time, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History returned (possibly empty)."),
            @ApiResponse(responseCode = "404", description = "The referenced patient does not exist.")
    })
    @GetMapping
    public ResponseEntity<ApiResponseBase<List<SbtResponse>>> getHistory(
            @RequestParam("patientId") UUID patientId) {
        List<SbtResponse> data = service.getHistory(patientId);

        return ResponseEntity.ok(
                ApiResponseBase.<List<SbtResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.SBT_HISTORY_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
