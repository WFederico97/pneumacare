package wfederico.pneumacare.shift.web;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shift.application.MedicalShiftService;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.web.dto.CreateShiftRequest;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for the medical shift lifecycle — PNMC-91.
 *
 * <p>Authorization ({@code ROLE_CHIEF_OF_GUARD}) is intentionally not enforced yet:
 * authentication/login is a separate backlog effort (next sprint). In dev all
 * {@code /api/**} endpoints are open, consistent with the other controllers.
 */

@Tag(name = "Shifts", description = "Medical shift open/close management")
@Slf4j
@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class MedicalShiftController {
    private final MedicalShiftService service;

    @Operation(
            summary = "Get the active shift for the current ICU",
            description = "Returns the OPEN shift for the caller's ICU, or null data when none is open.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active shift returned, or null when none is open."),
            @ApiResponse(responseCode = "401", description = "Unauthenticated (enforced once auth is implemented)."),
            @ApiResponse(responseCode = "403", description = "Authenticated without clinical access (deferred to auth USs).")
    })
    @PreAuthorize("hasRole('THERAPIST')")
    @GetMapping("/active")
    public ResponseEntity<ApiResponseBase<ShiftResponse>> getActiveShift(){
        ShiftResponse data = service.getActiveShift().orElse(null);
        return ResponseEntity.ok(
                ApiResponseBase.<ShiftResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message(data != null ?
                                RequestMessageConstants.SHIFT_ACTIVE_RETRIEVED :
                                RequestMessageConstants.NO_ACTIVE_SHIFT)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build()
        );
    }

    @Operation(
            summary = "Open a medical shift",
            description = "Opens a new OPEN shift for the given ICU. At most one OPEN shift may exist per ICU.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Shift opened; Location header points to the new resource."),
            @ApiResponse(responseCode = "400", description = "Missing or malformed icuId."),
            @ApiResponse(responseCode = "409", description = "The ICU already has an OPEN shift."),
            @ApiResponse(responseCode = "422", description = "The referenced ICU does not exist.")
    })
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    @PostMapping
    public ResponseEntity<ApiResponseBase<ShiftResponse>> openShift(@Valid @RequestBody CreateShiftRequest request){
        ShiftResponse data = service.open(request);

        URI location = URI.create("/api/v1/shifts/" + data.id());
        return ResponseEntity
                .created(location)
                .body(ApiResponseBase.<ShiftResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message(RequestMessageConstants.SHIFT_OPENED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build()
                );
    }
    @Operation(
            summary = "Close a medical shift",
            description = "Closes an OPEN shift, recording end_time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shift closed."),
            @ApiResponse(responseCode = "404", description = "No shift exists with the given id."),
            @ApiResponse(responseCode = "409", description = "The shift is already CLOSED.")
    })
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    @PatchMapping("/{id}/close")
    public ResponseEntity<ApiResponseBase<ShiftResponse>> closeShift(@PathVariable UUID id) {
        ShiftResponse data = service.close(id);

        return ResponseEntity.ok(
                ApiResponseBase.<ShiftResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.SHIFT_CLOSED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build()
        );

    }
}
