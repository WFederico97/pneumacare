package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "A single patient identifier (e.g. a DNI number). " +
        "The value is PII and is stored AES-256-GCM encrypted at rest.")
public record PatientIdentifierRequest(

        @Schema(
                description = "ID of the identifier type. Obtain the list of valid IDs " +
                        "from GET /api/v1/identifier-types.",
                example = "1",
                minimum = "1")
        @NotNull(message = "Identifier type ID is required")
        Integer identifierTypeId,

        @Schema(
                description = "The raw identifier value (e.g. DNI number '35123456', " +
                        "CUIL '20-35123456-4'). Stored AES-256-GCM encrypted at rest.",
                example = "35123456",
                maxLength = 50)
        @NotBlank(message = "Identifier value is required")
        @Size(max = 50, message = "Identifier value must not exceed 50 characters")
        String value) {
}
