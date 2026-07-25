package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;

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
 * <p>{@link #findByIdentity_IdAndClinicalStatus(UUID, ClinicalStatus)} is the
 * open-episode lookup: the service can verify that no OPEN {@code patients}
 * row already links to a given {@code patient_identities} record before
 * admitting a new episode (closed episodes are legitimate — readmission).
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
     * Looks up a person's episode in the given clinical status. With
     * {@code ADMITTED} this is the open-episode lookup (backed by the partial
     * unique index {@code uq_patients_open_episode}), used to prevent a second
     * concurrent admission of the same identity.
     *
     * @param identityId the UUID of the {@code patient_identities} record
     * @param status     the episode status to filter on
     * @return the matching episode, or empty
     */
    Optional<PatientJpaEntity> findByIdentity_IdAndClinicalStatus(UUID identityId, ClinicalStatus status);

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

    /** Admission timestamps of patients in a given clinical status (executive ALOS proxy). */
    @Query("select p.admissionDate from PatientJpaEntity p where p.clinicalStatus = :status")
    List<OffsetDateTime> findAdmissionDatesByClinicalStatus(ClinicalStatus status);

    /** Count of patients currently in a given airway state (analytics ventilation). */
    long countByRespiratoryStatus(RespiratoryStatus respiratoryStatus);

    /** Ids of patients currently in a given airway state (analytics WIND cohort). */
    @Query("select p.id from PatientJpaEntity p where p.respiratoryStatus = :status")
    List<UUID> findIdsByRespiratoryStatus(RespiratoryStatus status);

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

    /**
     * Closed episodes with a discharge in the window:
     * {@code [id, admissionDate, dischargeDate, disposition]} per row.
     * Feeds ALOS, turnover, mortality and readmission denominators.
     */
    @Query("select p.id, p.admissionDate, p.dischargeDate, p.disposition "
            + "from PatientJpaEntity p where p.dischargeDate >= :since")
    List<Object[]> findClosedEpisodeIntervals(OffsetDateTime since);

    /**
     * Readmission pairs: a later episode of the same identity admitted within
     * {@code :hours} of a prior episode's discharge, prior discharge in window.
     * Native SQL for the interval arithmetic.
     */
    @Query(value = """
            SELECT count(*) FROM patients p2
            JOIN patients p1 ON p1.identity_id = p2.identity_id AND p1.id <> p2.id
            WHERE p1.discharge_date IS NOT NULL
              AND p1.discharge_date >= :since
              AND p2.admission_date > p1.discharge_date
              AND p2.admission_date <= p1.discharge_date + make_interval(hours => :hours)
            """, nativeQuery = true)
    long countReadmissionsWithinHours(OffsetDateTime since, int hours);
}
