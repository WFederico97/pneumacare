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
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * REST controller for patient admission and identity management.
 *
 * <p>PII fields are always plain text in the request/response payloads —
 * AES-256-GCM encryption and decryption are handled transparently by the
 * JPA persistence layer ({@link wfederico.pneumacare.shared.security.encryption.AesAttributeConverter}).
 *
 * <h2>Security note</h2>
 * In {@code staging}/{@code prod} profiles these endpoints require OAuth2 scopes:
 * {@code SCOPE_write} for {@code POST}, {@code SCOPE_read} for {@code GET}.
 * In the {@code dev} profile all endpoints are open ({@code permitAll}).
 */
@Tag(name = "Patients", description = "Patient admission and PII identity management")
@Slf4j
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientIdentityService service;

    @Operation(
            summary = "Admit a new patient",
            description = """
                    Admits a patient to the ICU in a single atomic transaction:

                    1. Validates the ICU and bed (bed must belong to the ICU and be **AVAILABLE**).
                    2. Creates a `patient_identities` PII record with the given name and \
                    birth date, plus one `patient_identifiers` row for the supplied \
                    identifier. All PII values are stored **AES-256-GCM encrypted at rest**.
                    3. Creates the operational `patients` record linking identity, ICU, and bed.
                    4. Marks the bed **OCCUPIED**.

                    On any failure the whole transaction is rolled back.

                    Obtain valid `icuId` values from `GET /api/v1/icus` and valid \
                    `identifierTypeId` values from `GET /api/v1/identifier-types`.

                    **Required scope (staging/prod):** `SCOPE_write` — open in dev.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreatePatientRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "DNI",
                                            summary = "Admission with a DNI identifier",
                                            value = """
                                                    {
                                                      "firstName": "Juan",
                                                      "lastName": "Pérez",
                                                      "birthDate": "1989-05-14",
                                                      "identifier": { "identifierTypeId": 1, "value": "35123456" },
                                                      "icuId": "cccccccc-0000-0000-0000-000000000001",
                                                      "bedId": "dddddddd-0000-0000-0000-000000000001"
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "CUIL",
                                            summary = "Admission with a CUIL identifier",
                                            value = """
                                                    {
                                                      "firstName": "María",
                                                      "lastName": "González",
                                                      "birthDate": "1975-11-22",
                                                      "identifier": { "identifierTypeId": 2, "value": "27-12345678-4" },
                                                      "icuId": "cccccccc-0000-0000-0000-000000000001",
                                                      "bedId": "dddddddd-0000-0000-0000-000000000002"
                                                    }
                                                    """)
                            })))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Patient admitted successfully.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "status": 201,
                                              "message": "Patient admitted successfully",
                                              "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                                              "data": {
                                                "patientId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                                                "firstName": "Juan",
                                                "lastName": "Pérez",
                                                "birthDate": "1989-05-14",
                                                "identifier": { "typeName": "DNI", "value": "35123456" },
                                                "icuId": "cccccccc-0000-0000-0000-000000000001",
                                                "bedId": "dddddddd-0000-0000-0000-000000000001",
                                                "admissionDate": "2026-06-06T10:00:00-03:00",
                                                "clinicalStatus": "ADMITTED"
                                              }
                                            }
                                            """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error — one or more fields failed validation " +
                            "(blank name, missing/invalid identifier, missing icuId/bedId, " +
                            "bed not found, bed not AVAILABLE, unknown identifierTypeId, etc.).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required. Provide a valid Bearer token with " +
                            "scope `SCOPE_write` (staging/prod only).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
            @ApiResponse(
                    responseCode = "404",
                    description = "ICU not found.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PreAuthorize("hasRole('THERAPIST')")
    @PostMapping
    public ResponseEntity<ApiResponseBase<PatientResponse>> createPatient(
            @Valid @RequestBody CreatePatientRequest request) {

        log.debug("Patient admission requested: icuId={}, bedId={}", request.icuId(), request.bedId());
        PatientResponse data = service.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseBase.<PatientResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Patient admitted successfully")
                        .data(data)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @Operation(
            summary = "Get admitted patient by ID",
            description = """
                    Retrieves an admitted patient by their operational UUID ({@code patients.id}).

                    PII fields ({@code firstName}, {@code lastName}, and the identifier \
                    {@code value}) are decrypted transparently from AES-256-GCM \
                    storage before the response is sent. Callers always receive plain text.

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
                                                "patientId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                                                "firstName": "Juan",
                                                "lastName": "Pérez",
                                                "birthDate": "1989-05-14",
                                                "identifier": { "typeName": "DNI", "value": "35123456" },
                                                "icuId": "cccccccc-0000-0000-0000-000000000001",
                                                "bedId": "dddddddd-0000-0000-0000-000000000001",
                                                "admissionDate": "2026-06-06T10:00:00-03:00",
                                                "clinicalStatus": "ADMITTED"
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
                    description = "No admitted patient found with the given UUID.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
    })
    @PreAuthorize("hasAnyRole('THERAPIST','COMPLIANCE')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseBase<PatientResponse>> getPatient(
            @Parameter(description = "Operational patient UUID (patients.id).",
                    example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
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
