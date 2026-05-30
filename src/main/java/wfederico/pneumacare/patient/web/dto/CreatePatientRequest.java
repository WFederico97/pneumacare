package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/patients}.
 *
 * <p>All PII fields are received as plain text; the persistence layer
 * encrypts them transparently before writing to the database.
 *
 * <p>At least one identifier (e.g. DNI, CUIL) must be provided.
 * Each identifier references an existing {@code PatientIdentifierType}
 * by its {@code identifierTypeId}.
 */
@Schema(description = "Request payload for registering a new patient identity. " +
        "PII fields (firstName, lastName, identifier values) are stored encrypted " +
        "at rest (AES-256-GCM). Callers always send plain text.")
public record CreatePatientRequest(

        @Schema(
                description = "Patient first name. Stored AES-256-GCM encrypted at rest.",
                example = "Juan",
                maxLength = 100)
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Schema(
                description = "Patient last name. Stored AES-256-GCM encrypted at rest.",
                example = "Pérez",
                maxLength = 100)
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Schema(
                description = "Patient date of birth in ISO-8601 format (YYYY-MM-DD). Must be a past date.",
                example = "1989-05-14",
                type = "string",
                format = "date")
        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @Schema(
                description = "At least one patient identifier is required (e.g. DNI, CUIL). " +
                        "Obtain valid identifierTypeId values from GET /api/v1/identifier-types.",
                minLength = 1)
        @NotEmpty(message = "At least one identifier is required")
        List<@Valid PatientIdentifierRequest> identifiers) {
}
