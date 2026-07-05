package wfederico.pneumacare.inventory.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.inventory.application.AssetAssignmentService;
import wfederico.pneumacare.inventory.web.dto.AssetAssignmentResponse;
import wfederico.pneumacare.inventory.web.dto.AssignAssetRequest;
import wfederico.pneumacare.inventory.web.dto.UnassignAssetRequest;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;

/**
 * REST controller for linking physical ventilators to patients.
 *
 * <p>Assign/unassign are THERAPIST-secured action endpoints; each atomically
 * updates the assignment and the ventilator status.
 */
@Tag(name = "Assets", description = "Clinical hardware assignment")
@Slf4j
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetAssignmentController {

    private final AssetAssignmentService service;

    @Operation(summary = "Assign a ventilator to a patient",
            description = "Links an AVAILABLE ventilator to a patient and sets it IN_USE. "
                    + "A non-available ventilator is rejected with 400.")
    @PreAuthorize("hasRole('THERAPIST')")
    @PostMapping("/assign")
    public ResponseEntity<ApiResponseBase<AssetAssignmentResponse>> assign(
            @Valid @RequestBody AssignAssetRequest request) {
        AssetAssignmentResponse data = service.assign(request);
        return ResponseEntity.ok(
                envelope(HttpStatus.OK, RequestMessageConstants.ASSET_ASSIGNED, data));
    }

    @Operation(summary = "Release a ventilator's assignment",
            description = "Releases the ventilator's active assignment and sets it AVAILABLE.")
    @PreAuthorize("hasRole('THERAPIST')")
    @PostMapping("/unassign")
    public ResponseEntity<ApiResponseBase<AssetAssignmentResponse>> unassign(
            @Valid @RequestBody UnassignAssetRequest request) {
        AssetAssignmentResponse data = service.unassign(request);
        return ResponseEntity.ok(
                envelope(HttpStatus.OK, RequestMessageConstants.ASSET_UNASSIGNED, data));
    }

    private <T> ApiResponseBase<T> envelope(HttpStatus status, String message, T data) {
        return ApiResponseBase.<T>builder()
                .status(status.value())
                .message(message)
                .data(data)
                .traceId(MDC.get("traceId"))
                .build();
    }
}
