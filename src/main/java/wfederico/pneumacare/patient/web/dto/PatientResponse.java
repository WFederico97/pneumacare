package wfederico.pneumacare.patient.web.dto;

import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload for patient identity endpoints.
 *
 * <p>All PII fields are returned as plain text — the persistence layer
 * decrypts them transparently before this record is populated.
 * Each entry in {@link #identifiers} also carries a plain-text value
 * decrypted from {@code patient_identifiers.patient_identifier_name}.
 */
public record PatientResponse(
        UUID id,
        String firstName,
        String lastName,
        LocalDate birthDate,
        List<PatientIdentifierResponse> identifiers) {

    /** Maps a {@link PatientIdentityJpaEntity} (with already-decrypted fields) to this DTO. */
    public static PatientResponse from(PatientIdentityJpaEntity entity) {
        return new PatientResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getBirthDate(),
                entity.getIdentifiers().stream()
                        .map(PatientIdentifierResponse::from)
                        .toList());
    }
}
