package wfederico.pneumacare.procedures.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import wfederico.pneumacare.procedures.domain.ToleranceResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SbtRepository extends JpaRepository<SbtJpaEntity, UUID> {

    /** A patient's SBT history, newest first (by recorded time = created_at). */
    List<SbtJpaEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    /** Count of SBTs with a given outcome since the given instant (analytics weaning). */
    long countByToleranceResultAndCreatedAtAfter(ToleranceResult toleranceResult, OffsetDateTime since);
}
