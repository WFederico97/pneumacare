package wfederico.pneumacare.patient.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Nested request object representing a single patient identifier
 * (e.g. DNI "12345678" of type 1).
 *
 * <p>Used inside {@link CreatePatientRequest#identifiers()}.
 * The {@code value} field is PII — it is encrypted at rest by the persistence
 * layer via {@code AesAttributeConverter} before being stored in
 * {@code patient_identifiers.patient_identifier_name}.
 */
public record PatientIdentifierRequest(

        @NotNull(message = "Identifier type ID is required")
        Integer identifierTypeId,

        @NotBlank(message = "Identifier value is required")
        @Size(max = 50, message = "Identifier value must not exceed 50 characters")
        String value) {
}
