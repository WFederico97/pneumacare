package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Patient identity record returned by the API. " +
        "All PII fields (firstName, lastName, identifier values) are plain text — " +
        "decryption is handled transparently by the JPA layer.")
public record PatientResponse(

        @Schema(
                description = "Unique UUID assigned to this patient identity record.",
                example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,

        @Schema(
                description = "Patient first name, decrypted from AES-256-GCM storage.",
                example = "Juan")
        String firstName,

        @Schema(
                description = "Patient last name, decrypted from AES-256-GCM storage.",
                example = "Pérez")
        String lastName,

        @Schema(
                description = "Patient date of birth (ISO-8601).",
                example = "1989-05-14",
                type = "string",
                format = "date")
        LocalDate birthDate,

        @Schema(
                description = "List of patient identifiers (DNI, CUIL, etc.). " +
                        "Each value is decrypted from AES-256-GCM storage.")
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
