package wfederico.pneumacare.patient.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.patient.application.PatientIdentifierTypeService;
import wfederico.pneumacare.patient.web.dto.IdentifierTypeResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.List;

/**
 * REST controller for the patient identifier type catalog.
 *
 * <p>Exposes a single read-only endpoint used by the frontend to populate
 * the identifier type dropdown during patient registration. The catalog
 * is seeded by Flyway (staging/prod) or
 * {@link wfederico.pneumacare.patient.infrastructure.IdentifierTypeDataSeeder} (dev).
 *
 * <p>No PII is involved — identifier type names (e.g. "DNI") are generic labels.
 * No authentication scope is required beyond the base rate-limit filter.
 */
@Tag(name = "Identifier Types", description = "Catalog of patient identifier types (DNI, CUIL, CUIT, Pasaporte, etc.)")
@RestController
@RequestMapping("/api/v1/identifier-types")
@RequiredArgsConstructor
public class IdentifierTypeController {

    private final PatientIdentifierTypeService service;

    @Operation(
            summary = "List all identifier types",
            description = "Returns the full catalog of patient identifier types, ordered by insertion order. "
                        + "Used to populate the identifier type dropdown on the patient registration form.")
    @GetMapping
    public ResponseEntity<ApiResponseBase<List<IdentifierTypeResponse>>> listIdentifierTypes() {
        List<IdentifierTypeResponse> data = service.findAll();
        return ResponseEntity.ok(
                ApiResponseBase.<List<IdentifierTypeResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message("Identifier types retrieved successfully")
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
