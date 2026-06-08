package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response payload for patient admission and retrieval endpoints.
 *
 * <p>All PII fields ({@code firstName}, {@code lastName}, and the identifier
 * value) are returned as plain text — the JPA persistence layer decrypts them
 * transparently from AES-256-GCM storage before this record is populated.
 *
 * <h2>UUID semantics</h2>
 * {@link #patientId} is the operational record UUID ({@code patients.id}). This
 * is the canonical patient identifier referenced by all clinical tables
 * (evaluations, airway assessments, SBT, etc.). The internal PII identity UUID
 * ({@code patient_identities.id}) is not exposed — callers have no need for it.
 */
@Schema(description = "Admission response. All PII fields are plain text — " +
        "decryption is handled transparently by the JPA layer.")
public record PatientResponse(

        @Schema(
                description = "Operational patient UUID. This is the ID referenced by all " +
                        "clinical tables (evaluations, airway assessments, etc.).",
                example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID patientId,

        @Schema(description = "Patient first name, decrypted from AES-256-GCM storage.", example = "Juan")
        String firstName,

        @Schema(description = "Patient last name, decrypted from AES-256-GCM storage.", example = "Pérez")
        String lastName,

        @Schema(
                description = "Patient date of birth (ISO-8601).",
                example = "1989-05-14",
                type = "string",
                format = "date")
        java.time.LocalDate birthDate,

        @Schema(description = "The patient's identifier (type + value), decrypted from AES-256-GCM storage.")
        PatientIdentifierResponse identifier,

        @Schema(
                description = "UUID of the ICU the patient was admitted to.",
                example = "cccccccc-0000-0000-0000-000000000001")
        UUID icuId,

        @Schema(
                description = "UUID of the bed assigned to this patient. Null if no bed was assigned.",
                example = "dddddddd-0000-0000-0000-000000000001",
                nullable = true)
        UUID bedId,

        @Schema(
                description = "ISO-8601 timestamp of admission (timezone-aware).",
                example = "2026-06-06T10:00:00-03:00",
                type = "string",
                format = "date-time")
        OffsetDateTime admissionDate,

        @Schema(description = "Current clinical status of the patient.", example = "ADMITTED")
        String clinicalStatus) {

    /**
     * Maps a fully-loaded {@link PatientJpaEntity} to this response DTO.
     *
     * <p>The single patient identifier (first entry in the identity's identifier
     * list) is mapped to a {@link PatientIdentifierResponse}. The caller must
     * ensure that the entity was loaded with its
     * {@code identity.identifiers.patientIdentifierType} association populated
     * (e.g. via {@code @EntityGraph}) to avoid lazy-load exceptions.
     *
     * @param entity a fully-populated operational patient entity
     * @return the admission response DTO with plain-text PII
     */
    public static PatientResponse from(PatientJpaEntity entity) {
        var identity = entity.getIdentity();

        PatientIdentifierResponse identifierResponse = identity.getIdentifiers().isEmpty()
                ? null
                : PatientIdentifierResponse.from(identity.getIdentifiers().get(0));

        return new PatientResponse(
                entity.getId(),
                identity.getFirstName(),
                identity.getLastName(),
                identity.getBirthDate(),
                identifierResponse,
                entity.getIcu().getId(),
                entity.getBed() != null ? entity.getBed().getId() : null,
                entity.getAdmissionDate(),
                entity.getClinicalStatus().name());
    }
}
