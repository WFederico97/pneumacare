package wfederico.pneumacare.patient.web.dto;

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
public record CreatePatientRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @NotEmpty(message = "At least one identifier is required")
        List<@Valid PatientIdentifierRequest> identifiers) {
}
