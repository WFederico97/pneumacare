package wfederico.pneumacare.shift.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shift.application.ShiftHandoverService;
import wfederico.pneumacare.shift.web.dto.CreateHandoverRequest;
import wfederico.pneumacare.shift.web.dto.HandoverResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for shift handover notes — PNMC-92.
 *
 * <p>Authorization ({@code ROLE_THERAPIST} / {@code ROLE_CHIEF_OF_GUARD}) is
 * intentionally not enforced yet: authentication/login is a separate backlog
 * effort (next sprint). In dev all {@code /api/**} endpoints are open, consistent
 * with the other controllers.
 */
@Tag(name = "Shift Handovers", description = "Immutable handover notes submitted against an OPEN shift")
@Slf4j
@RestController
@RequestMapping("/api/v1/shifts/{shiftId}/handovers")
@RequiredArgsConstructor
public class ShiftHandoverController {

    private final ShiftHandoverService service;

    @Operation(
            summary = "Submit a handover note",
            description = "Adds an immutable handover note to an OPEN shift. The author is derived "
                    + "from the authenticated principal.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Note created."),
            @ApiResponse(responseCode = "404", description = "No shift exists with the given id."),
            @ApiResponse(responseCode = "409", description = "The shift is CLOSED."),
            @ApiResponse(responseCode = "422", description = "notesContent is empty or exceeds 4000 chars.")
    })
    @PostMapping
    public ResponseEntity<ApiResponseBase<HandoverResponse>> createHandover(
            @PathVariable UUID shiftId,
            @RequestBody CreateHandoverRequest request) {
        HandoverResponse data = service.create(shiftId, request);

        URI location = URI.create("/api/v1/shifts/" + shiftId + "/handovers");
        return ResponseEntity
                .created(location)
                .body(ApiResponseBase.<HandoverResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message(RequestMessageConstants.HANDOVER_CREATED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @Operation(
            summary = "List a shift's handover notes",
            description = "Returns the shift's handover notes ordered by creation time, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notes returned (possibly empty)."),
            @ApiResponse(responseCode = "404", description = "No shift exists with the given id.")
    })
    @GetMapping
    public ResponseEntity<ApiResponseBase<List<HandoverResponse>>> getHandovers(
            @PathVariable UUID shiftId) {
        List<HandoverResponse> data = service.getForShift(shiftId);

        return ResponseEntity.ok(
                ApiResponseBase.<List<HandoverResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.HANDOVERS_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
