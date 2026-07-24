package wfederico.pneumacare.procedures.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import wfederico.pneumacare.procedures.domain.ToleranceResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SbtRepository extends JpaRepository<SbtJpaEntity, UUID> {

    /** A patient's SBT history, newest first (by recorded time = created_at). */
    List<SbtJpaEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    /** Count of SBTs with a given outcome since the given instant (analytics weaning). */
    long countByToleranceResultAndCreatedAtAfter(ToleranceResult toleranceResult, OffsetDateTime since);

    /** Per-patient SBT attempt counts since the given instant (WIND classification). */
    @Query("select s.patientId as patientId, count(s) as total from SbtJpaEntity s " +
           "where s.createdAt >= :since group by s.patientId")
    List<PatientSbtCount> countAttemptsByPatientSince(OffsetDateTime since);

    /** Patients with at least one failed SBT (weaning-failure cohort seed, executive analytics). */
    @Query("select distinct s.patientId from SbtJpaEntity s "
            + "where s.toleranceResult = wfederico.pneumacare.procedures.domain.ToleranceResult.FAILURE")
    List<UUID> findPatientIdsWithFailedSbt();

    /** Projection for {@link #countAttemptsByPatientSince(OffsetDateTime)}. */
    interface PatientSbtCount {
        UUID getPatientId();
        long getTotal();
    }
}
