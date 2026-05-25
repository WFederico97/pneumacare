package wfederico.pneumacare.patient.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
 * <h3>Security note</h3>
 * In {@code staging}/{@code prod} profiles these endpoints require OAuth2 scopes:
 * {@code SCOPE_write} for {@code POST}, {@code SCOPE_read} for {@code GET}.
 */
@Tag(name = "Patients", description = "Patient registration and PII identity management")
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientIdentityService service;

    @Operation(
            summary  = "Register a new patient",
            description = "Creates a patient identity record. PII fields (name, national ID) " +
                          "are stored encrypted with AES-256-GCM. The response always returns " +
                          "plain text (decryption is transparent).")
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
            summary  = "Get patient by ID",
            description = "Retrieves a patient identity by UUID. " +
                          "PII fields are decrypted transparently before the response is sent.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseBase<PatientResponse>> getPatient(
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
