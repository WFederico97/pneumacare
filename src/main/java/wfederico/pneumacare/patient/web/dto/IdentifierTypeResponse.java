package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;

/**
 * Response representation of a single patient identifier type catalog entry.
 *
 * <p>Identifier type names and descriptions are not PII — they are generic
 * labels (e.g. "DNI", "CUIL"). No encryption applies to this DTO.
 */
@Schema(description = "A catalog entry representing an identifier type. " +
        "Not PII — these are generic labels, not personal data.")
public record IdentifierTypeResponse(

        @Schema(
                description = "Numeric primary key of the identifier type. " +
                        "Use this value as identifierTypeId in POST /api/v1/patients.",
                example = "1")
        int id,

        @Schema(
                description = "Short code of the identifier type shown in the UI.",
                example = "DNI")
        String name,

        @Schema(
                description = "Human-readable description of the identifier type.",
                example = "Documento Nacional de Identidad")
        String description) {

    /** Maps a {@link PatientIdentifierTypeJpaEntity} to this DTO. */
    public static IdentifierTypeResponse from(PatientIdentifierTypeJpaEntity entity) {
        return new IdentifierTypeResponse(
                entity.getPatientIdentifierTypeId(),
                entity.getPatientIdentifierTypeName(),
                entity.getPatientIdentifierTypeDescription());
    }
}
