package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/patients} — patient admission.
 *
 * <p>PII fields ({@code firstName}, {@code lastName}, and the identifier value)
 * are received as plain text. The persistence layer encrypts them transparently
 * (AES-256-GCM) before writing to the database.
 *
 * <h2>Identifier handling</h2>
 * A single patient identifier is required. The caller selects a type from
 * {@code GET /api/v1/identifier-types} (e.g. DNI, Pasaporte) and provides the
 * corresponding value. No type-specific format validation is applied at this
 * layer — the value is stored and encrypted as-is.
 *
 * <h2>Bed assignment</h2>
 * The caller supplies only {@code bedId}. The ICU is derived server-side from
 * the session, and the service validates that the bed belongs to it and is
 * currently {@code AVAILABLE} before creating the admission record. There is
 * deliberately no {@code icuId} field: when the client supplied one it could
 * disagree with the bed's real ICU, which made every admission fail.
 */
@Schema(description = "Request payload for admitting a patient. " +
        "PII fields (firstName, lastName, identifier value) are stored " +
        "AES-256-GCM encrypted at rest. Callers always send plain text.")
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
                description = "The patient's identifier. Select an identifier type from " +
                        "GET /api/v1/identifier-types and supply the corresponding value. " +
                        "The value is stored AES-256-GCM encrypted at rest.",
                example = "{\"identifierTypeId\": 1, \"value\": \"35123456\"}")
        @NotNull(message = "Identifier is required")
        @Valid
        PatientIdentifierRequest identifier,

        @Schema(
                description = "UUID of the bed to assign to this patient. " +
                        "The bed must belong to the session's ICU and have status AVAILABLE.",
                example = "dddddddd-0000-0000-0000-000000000001")
        @NotNull(message = "Bed ID is required")
        UUID bedId) {
}
