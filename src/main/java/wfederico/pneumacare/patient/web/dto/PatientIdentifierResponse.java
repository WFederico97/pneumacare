package wfederico.pneumacare.patient.web.dto;

import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierJpaEntity;

/**
 * Response representation of a single patient identifier.
 *
 * <p>The {@code value} field is always plain text — the persistence layer
 * decrypts {@code patient_identifiers.patient_identifier_name} (AES-256-GCM)
 * transparently before this record is populated.
 */
public record PatientIdentifierResponse(
        String typeName,
        String value) {

    /** Maps a {@link PatientIdentifierJpaEntity} (with already-decrypted value) to this DTO. */
    public static PatientIdentifierResponse from(PatientIdentifierJpaEntity entity) {
        return new PatientIdentifierResponse(
                entity.getPatientIdentifierType().getPatientIdentifierTypeName(),
                entity.getPatientIdentifierName());
    }
}
