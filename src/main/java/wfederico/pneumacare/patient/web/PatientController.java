package wfederico.pneumacare.patient.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.patient.application.PatientIdentityService;
import wfederico.pneumacare.patient.web.dto.CreatePatientRequest;
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.web.ApiResponseBase;

import java.util.UUID;

/**
 * REST controller for patient identity management.
 *
 * <p>PII fields are always plain text in the request/response payloads —
 * AES-256-GCM encryption and decryption are handled transparently by the
 * JPA persistence layer.
 *
 * <h2>Security note</h2>
 * In {@code staging}/{@code prod} profiles these endpoints require OAuth2 scopes:
 * {@code SCOPE_write} for {@code POST}, {@code SCOPE_read} for {@code GET}.
 * In the {@code dev} profile all endpoints are open ({@code permitAll}).
 */
@Tag(name = "Patients", description = "Patient registration and PII identity management")
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientIdentityService service;

    @Operation(
            summary = "Register a new patient",
            description = """
                    Creates a patient identity record with one or more structured identifiers \
                    (DNI, CUIL, CUIT, Passport, etc.).

                    **PII fields** (`firstName`, `lastName`, and each identifier `value`) are \
                    stored encrypted at rest using AES-256-GCM with a random 12-byte IV per write. \
                    The response always returns plain text — decryption is transparent.

                    Obtain valid `identifierTypeId` values from `GET /api/v1/identifier-types` \
                    before calling this endpoint.

                    **Required scope (staging/prod):** `SCOPE_write` — open in dev.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreatePatientRequest.class),
                            examples = @ExampleObject(
                                    name = "DNI example",
                                    summary = "Patient with a single DNI identifier",
                                    value = """
                                            {
                                              "firstName": "Juan",
                                              "lastName": "Pérez",
                                              "birthDate": "1989-05-14",
                                              "identifiers": [
                                                { "identifierTypeId": 1, "value": "35123456" }
                                              ]
                                            }
                                            """))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Patient registered successfully.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "status": 201,
                                              "message": "Patient registered successfully",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "data": {
                                                "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                                                "firstName": "Juan",
                                                "lastName": "Pérez",
                                                "birthDate": "1989-05-14",
                                                "identifiers": [
                                                  { "typeName": "DNI", "value": "35123456" }
                                                ]
                                              }
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error — one or more fields failed validation " +
                            "(blank name, missing birthDate, empty identifiers list, " +
                            "unknown identifierTypeId, etc.).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required. Provide a valid Bearer token with " +
                            "scope `SCOPE_write` (staging/prod only).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PostMapping
    public ResponseEntity<ApiResponseBase<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest request) {

        PatientResponse data = service.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseBase.<PatientResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Patient registered successfully")
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @Operation(
            summary = "Get patient by ID",
            description = """
                    Retrieves a patient identity record by its UUID.

                    PII fields (`firstName`, `lastName`, and each identifier `value`) are \
                    decrypted transparently from AES-256-GCM storage before the response is sent. \
                    Callers always receive plain text.

                    **Required scope (staging/prod):** `SCOPE_read` — open in dev.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Patient record retrieved successfully.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "status": 200,
                                              "message": "Patient retrieved successfully",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "data": {
                                                "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                                                "firstName": "Juan",
                                                "lastName": "Pérez",
                                                "birthDate": "1989-05-14",
                                                "identifiers": [
                                                  { "typeName": "DNI", "value": "35123456" }
                                                ]
                                              }
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required. Provide a valid Bearer token with " +
                            "scope `SCOPE_read` (staging/prod only).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(
                    responseCode = "404",
                    description = "No patient found with the given UUID.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseBase<PatientResponse>> getPatient(
            @Parameter(description = "UUID of the patient identity record.", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
            @PathVariable UUID id) {

        PatientResponse data = service.findById(id);
        return ResponseEntity.ok(
                ApiResponseBase.<PatientResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message("Patient retrieved successfully")
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }
}
