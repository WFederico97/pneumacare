package wfederico.pneumacare.patient.web.dto;

import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;

/**
 * Response representation of a single patient identifier type catalog entry.
 *
 * <p>Identifier type names and descriptions are not PII — they are generic
 * labels (e.g. "DNI", "CUIL"). No encryption applies to this DTO.
 */
public record IdentifierTypeResponse(
        int id,
        String name,
        String description) {

    /** Maps a {@link PatientIdentifierTypeJpaEntity} to this DTO. */
    public static IdentifierTypeResponse from(PatientIdentifierTypeJpaEntity entity) {
        return new IdentifierTypeResponse(
                entity.getPatientIdentifierTypeId(),
                entity.getPatientIdentifierTypeName(),
                entity.getPatientIdentifierTypeDescription());
    }
}
