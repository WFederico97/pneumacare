package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierJpaEntity;

/**
 * Response representation of a single patient identifier.
 *
 * <p>The {@code value} field is always plain text — the persistence layer
 * decrypts {@code patient_identifiers.patient_identifier_name} (AES-256-GCM)
 * transparently before this record is populated.
 */
@Schema(description = "A single patient identifier entry in a response. " +
        "The value is always plain text — decryption is transparent.")
public record PatientIdentifierResponse(

        @Schema(
                description = "Short name of the identifier type (e.g. DNI, CUIL, CUIT, Pasaporte).",
                example = "DNI")
        String typeName,

        @Schema(
                description = "The raw identifier value, decrypted from AES-256-GCM storage.",
                example = "35123456")
        String value) {

    /** Maps a {@link PatientIdentifierJpaEntity} (with already-decrypted value) to this DTO. */
    public static PatientIdentifierResponse from(PatientIdentifierJpaEntity entity) {
        return new PatientIdentifierResponse(
                entity.getPatientIdentifierType().getPatientIdentifierTypeName(),
                entity.getPatientIdentifierName());
    }
}
