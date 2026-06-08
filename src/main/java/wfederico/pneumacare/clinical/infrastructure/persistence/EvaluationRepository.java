package wfederico.pneumacare.clinical.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EvaluationJpaEntity}.
 *
 * <p>Provides standard CRUD operations for the {@code evaluations} table.
 * Evaluations are immutable after insertion — no update operations are expected.
 */
@Repository
public interface EvaluationRepository extends JpaRepository<EvaluationJpaEntity, UUID> {
}
