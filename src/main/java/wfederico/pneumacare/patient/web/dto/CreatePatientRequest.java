package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import wfederico.pneumacare.patient.web.dto.validation.Dni;
import wfederico.pneumacare.patient.web.dto.validation.NoDniInList;
import wfederico.pneumacare.shared.constants.ExceptionMessageConstants;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/patients} — patient admission.
 *
 * <p>PII fields ({@code firstName}, {@code lastName}, {@code dni}) and any
 * additional identifier values are received as plain text. The persistence layer
 * encrypts them transparently (AES-256-GCM) before writing to the database.
 *
 * <h2>DNI handling</h2>
 * The Argentine national identity number is promoted to a dedicated, required
 * top-level field with format validation ({@code @Dni}). It must <em>not</em>
 * appear again inside {@code additionalIdentifiers} — the class-level
 * {@code @NoDniInList} constraint enforces this.
 *
 * <h2>Bed assignment</h2>
 * The caller must supply both {@code icuId} and {@code bedId}. The service
 * validates that the bed belongs to the given ICU and is currently
 * {@code AVAILABLE} before creating the admission record.
 */
@Schema(description = "Request payload for admitting a patient. " +
        "PII fields (firstName, lastName, dni, additional identifier values) are stored " +
        "AES-256-GCM encrypted at rest. Callers always send plain text.")
@NoDniInList
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
                description = "[PII] Argentine national identity number (DNI). " +
                        "Must be 7 or 8 numeric digits (no dots or spaces). " +
                        "Stored AES-256-GCM encrypted at rest.",
                example = "35123456")
        @NotBlank(message = ExceptionMessageConstants.DNI_REQUIRED)
        @Dni
        String dni,

        @Schema(
                description = "UUID of the Intensive Care Unit where the patient is being admitted. " +
                        "Obtain valid IDs from GET /api/v1/icus.",
                example = "cccccccc-0000-0000-0000-000000000001")
        @NotNull(message = "ICU ID is required")
        UUID icuId,

        @Schema(
                description = "UUID of the bed to assign to this patient. " +
                        "The bed must belong to the given icuId and have status AVAILABLE.",
                example = "dddddddd-0000-0000-0000-000000000001")
        @NotNull(message = "Bed ID is required")
        UUID bedId,

        @Schema(
                description = "Optional list of additional patient identifiers (CUIL, CUIT, Passport, etc.). " +
                        "Must not contain a DNI entry — use the top-level 'dni' field instead. " +
                        "Obtain valid identifierTypeId values from GET /api/v1/identifier-types.")
        List<@Valid PatientIdentifierRequest> additionalIdentifiers) {
}
