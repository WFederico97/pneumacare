package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PatientJpaEntity}.
 *
 * <p>{@link #findById(UUID)} uses an {@link EntityGraph} to eagerly fetch
 * the associated {@link IcuJpaEntity}, {@link IcuBedJpaEntity}, and
 * {@link PatientIdentityJpaEntity} (with its identifiers and their types)
 * in as few queries as possible, avoiding N+1 problems when building the
 * admission response.
 *
 * <p>{@link #findByIdentity_Id(UUID)} is provided for idempotency checks:
 * the service can verify that no {@code patients} row already links to a given
 * {@code patient_identities} record before creating a new one.
 */
@Repository
public interface PatientRepository extends JpaRepository<PatientJpaEntity, UUID> {

    /**
     * Loads the patient together with its ICU, bed, and PII identity
     * (including nested identifiers and their type labels) in a single query.
     *
     * @param id the operational patient UUID
     * @return the fully populated patient, or empty if not found
     */
    @EntityGraph(attributePaths = {
            "icu",
            "bed",
            "bed.icu",
            "identity",
            "identity.identifiers",
            "identity.identifiers.patientIdentifierType"
    })
    @Override
    Optional<PatientJpaEntity> findById(UUID id);

    /**
     * Looks up the operational patient record linked to a given PII identity.
     * Used to prevent duplicate admissions for the same identity.
     *
     * @param identityId the UUID of the {@code patient_identities} record
     * @return the patient linked to that identity, or empty if none exists
     */
    Optional<PatientJpaEntity> findByIdentity_Id(UUID identityId);
}
