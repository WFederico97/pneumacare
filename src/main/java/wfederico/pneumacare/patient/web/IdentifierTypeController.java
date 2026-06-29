package wfederico.pneumacare.patient.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            description = """
                    Returns the full catalog of patient identifier types, ordered by insertion order \
                    (DNI first, then CUIL, CUIT, LE, LC, Pasaporte).

                    Use the `id` field of each entry as the `identifierTypeId` value when calling \
                    `POST /api/v1/patients`.

                    No authentication scope is required for this endpoint.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Catalog retrieved successfully.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "status": 200,
                                              "message": "Identifier types retrieved successfully",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "data": [
                                                { "id": 1, "name": "DNI",       "description": "Documento Nacional de Identidad" },
                                                { "id": 2, "name": "CUIL",      "description": "Código Único de Identificación Laboral" },
                                                { "id": 3, "name": "CUIT",      "description": "Código Único de Identificación Tributaria" },
                                                { "id": 4, "name": "LE",        "description": "Libreta de Enrolamiento" },
                                                { "id": 5, "name": "LC",        "description": "Libreta Cívica" },
                                                { "id": 6, "name": "Pasaporte", "description": "Pasaporte" }
                                              ]
                                            }
                                            """)))
    })
    @PreAuthorize("permitAll()")
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
