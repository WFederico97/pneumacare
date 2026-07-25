package wfederico.pneumacare.inventory.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.inventory.application.VentilatorService;
import wfederico.pneumacare.inventory.web.dto.CreateVentilatorRequest;
import wfederico.pneumacare.inventory.web.dto.UpdateVentilatorStatusRequest;
import wfederico.pneumacare.inventory.web.dto.VentilatorResponse;
import wfederico.pneumacare.shared.constants.RequestMessageConstants;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shared.web.dto.PageResponse;

import java.util.UUID;

/**
 * REST controller for physical ventilator inventory management.
 *
 * <p>Writes are restricted to administrators and chiefs of guard; reads are
 * open to all clinical staff so ventilators can be selected during evaluations.
 */
@Tag(name = "Ventilators", description = "Physical ventilator inventory management")
@Slf4j
@RestController
@RequestMapping("/api/v1/ventilators")
@RequiredArgsConstructor
public class VentilatorController {

    private static final int MAX_PAGE_SIZE = 100;

    private final VentilatorService service;

    @Operation(summary = "Register a physical ventilator",
            description = "Registers a ventilator with a unique serial number. "
                    + "Duplicate serials are rejected with 409 Conflict.")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_OF_GUARD')")
    @PostMapping
    public ResponseEntity<ApiResponseBase<VentilatorResponse>> create(
            @Valid @RequestBody CreateVentilatorRequest request) {
        VentilatorResponse data = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(envelope(HttpStatus.CREATED, RequestMessageConstants.VENTILATOR_CREATED, data));
    }

    @Operation(summary = "List ventilators (paginated)",
            description = "Pages the ventilator inventory of the caller's ICU.")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_OF_GUARD','THERAPIST')")
    @GetMapping
    public ResponseEntity<ApiResponseBase<PageResponse<VentilatorResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("serialNumber").ascending());
        PageResponse<VentilatorResponse> data = service.list(pageable);
        return ResponseEntity.ok(
                envelope(HttpStatus.OK, RequestMessageConstants.VENTILATORS_RETRIEVED, data));
    }

    @Operation(summary = "Get a ventilator by id")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_OF_GUARD','THERAPIST')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseBase<VentilatorResponse>> getById(@PathVariable UUID id) {
        VentilatorResponse data = service.getById(id);
        return ResponseEntity.ok(
                envelope(HttpStatus.OK, RequestMessageConstants.VENTILATOR_RETRIEVED, data));
    }

    @Operation(summary = "Update a ventilator's status",
            description = "Status-only partial update (AVAILABLE / IN_USE / MAINTENANCE).")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_OF_GUARD')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseBase<VentilatorResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVentilatorStatusRequest request) {
        VentilatorResponse data = service.updateStatus(id, request);
        return ResponseEntity.ok(
                envelope(HttpStatus.OK, RequestMessageConstants.VENTILATOR_STATUS_UPDATED, data));
    }

    @Operation(summary = "Delete a ventilator",
            description = "Hard delete. Ventilators referenced by clinical history "
                    + "cannot be deleted (409) — set them to MAINTENANCE instead.")
    @PreAuthorize("hasAnyRole('ADMIN','CHIEF_OF_GUARD')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
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
