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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shift.application.ShiftAuditService;
import wfederico.pneumacare.shift.web.dto.AuditRevisionResponse;
import wfederico.pneumacare.shift.web.dto.HandoverResponse;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.util.List;
import java.util.UUID;

/**
 * Read-only audit history API for shift-context records (PNMC-134).
 *
 * <p>Both endpoints require the {@code SCOPE_audit} authority, enforced by
 * {@link PreAuthorize}. {@code GlobalExceptionHandler} maps the resulting
 * {@code AccessDeniedException} to {@code 401} for anonymous callers and {@code 403}
 * for authenticated callers without the scope.
 *
 * <p>In staging/prod these GET endpoints also sit behind the {@code SCOPE_read} request
 * matcher, so a compliance principal needs both {@code SCOPE_read} and {@code SCOPE_audit}.
 */
@Tag(name = "Shift Audit", description = "Envers revision history for shifts and handovers (compliance role)")
@Slf4j
@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
public class ShiftAuditController {

    private final ShiftAuditService service;

    @Operation(
            summary = "Get a shift's audit history",
            description = "Returns the Envers revision history (actor, timestamp, snapshot) for a shift. "
                    + "Requires the SCOPE_audit authority.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revision history returned (possibly empty)."),
            @ApiResponse(responseCode = "401", description = "Unauthenticated."),
            @ApiResponse(responseCode = "403", description = "Authenticated without the compliance/audit authority.")
    })
    @GetMapping("/{id}/audit")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<ApiResponseBase<List<AuditRevisionResponse<ShiftResponse>>>> getShiftAudit(
            @PathVariable UUID id) {
        List<AuditRevisionResponse<ShiftResponse>> data = service.getShiftHistory(id);

        return ResponseEntity.ok(
                ApiResponseBase.<List<AuditRevisionResponse<ShiftResponse>>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.SHIFT_AUDIT_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @Operation(
            summary = "Get a handover note's audit history",
            description = "Returns the Envers revision history for a handover note. "
                    + "Requires the SCOPE_audit authority.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revision history returned (possibly empty)."),
            @ApiResponse(responseCode = "401", description = "Unauthenticated."),
            @ApiResponse(responseCode = "403", description = "Authenticated without the compliance/audit authority.")
    })
    @GetMapping("/handovers/{id}/audit")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ResponseEntity<ApiResponseBase<List<AuditRevisionResponse<HandoverResponse>>>> getHandoverAudit(
            @PathVariable UUID id) {
        List<AuditRevisionResponse<HandoverResponse>> data = service.getHandoverHistory(id);

        return ResponseEntity.ok(
                ApiResponseBase.<List<AuditRevisionResponse<HandoverResponse>>>builder()
                        .status(HttpStatus.OK.value())
                        .message(RequestMessageConstants.HANDOVER_AUDIT_RETRIEVED)
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
