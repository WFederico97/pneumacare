package wfederico.pneumacare.clinical.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ClinicalConsultantInsightJpaEntity}.
 */
@Repository
public interface ClinicalConsultantInsightRepository
        extends JpaRepository<ClinicalConsultantInsightJpaEntity, UUID> {

    /** The cached insight for an evaluation, if one has been composed. */
    Optional<ClinicalConsultantInsightJpaEntity> findByEvaluationId(UUID evaluationId);
}
