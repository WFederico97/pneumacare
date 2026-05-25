package wfederico.pneumacare.patient.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/patients}.
 *
 * <p>All PII fields are received as plain text; the persistence layer
 * encrypts them transparently before writing to the database.
 */
public record CreatePatientRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotBlank(message = "National ID is required")
        @Size(max = 20, message = "National ID must not exceed 20 characters")
        String nationalId,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate) {
}
