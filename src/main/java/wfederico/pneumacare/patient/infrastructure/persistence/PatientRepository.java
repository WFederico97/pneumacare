package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import wfederico.pneumacare.patient.domain.ClinicalStatus;

import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * Looks up the patient currently occupying the given bed with the given clinical status.
     * Used to enrich the {@code IcuBedResponse} with the admitted patient's UUID.
     *
     * @param bedId          the UUID of the {@code icu_beds} record
     * @param clinicalStatus the status to filter on (typically {@code ADMITTED})
     * @return the matching patient, or empty if none
     */
    Optional<PatientJpaEntity> findByBed_IdAndClinicalStatus(UUID bedId, ClinicalStatus clinicalStatus);

    /** Count of patients admitted since the given instant (analytics ward). */
    long countByAdmissionDateAfter(OffsetDateTime since);

    /**
     * All patients, newest admission first, with the full PII + bed/ICU graph
     * eagerly loaded so {@code PatientResponse.from} can map them without N+1
     * queries or lazy-load failures.
     */
    @EntityGraph(attributePaths = {
            "icu",
            "bed",
            "identity",
            "identity.identifiers",
            "identity.identifiers.patientIdentifierType"
    })
    List<PatientJpaEntity> findAllByOrderByAdmissionDateDesc();

    /**
     * Projects the bed number/label currently assigned to a patient.
     * The inner join excludes patients with no bed, so the result is empty when
     * no bed is assigned (or the patient does not exist).
     *
     * @param patientId the operational patient UUID
     * @return the bed number/label, or empty if unassigned or patient absent
     */
    @Query("select b.bedNumber from PatientJpaEntity p join p.bed b where p.id = :patientId")
    Optional<String> findBedLabelByPatientId(UUID patientId);
}
