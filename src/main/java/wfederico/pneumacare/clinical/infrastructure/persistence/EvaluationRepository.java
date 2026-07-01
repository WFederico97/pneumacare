package wfederico.pneumacare.clinical.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EvaluationJpaEntity}.
 *
 * <p>Provides standard CRUD operations for the {@code evaluations} table.
 * Evaluations are immutable after insertion — no update operations are expected.
 */
@Repository
public interface EvaluationRepository extends JpaRepository<EvaluationJpaEntity, UUID> {

    /** A patient's evaluations, newest first (by recorded evaluation time). */
    List<EvaluationJpaEntity> findByPatientIdOrderByEvaluationTimeDesc(UUID patientId);

    /** Total evaluations recorded since the given instant (analytics). */
    long countByEvaluationTimeAfter(OffsetDateTime since);

    /** Evaluations since the given instant in a given RSBI band (analytics weaning). */
    long countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation interpretation, OffsetDateTime since);

    /** Per-day evaluation counts since the given instant (analytics trend). */
    @Query("select cast(e.evaluationTime as localdate) as day, count(e) as total " +
           "from EvaluationJpaEntity e where e.evaluationTime >= :since " +
           "group by cast(e.evaluationTime as localdate)")
    List<DailyCount> countDailySince(OffsetDateTime since);

    /** Projection for {@link #countDailySince(OffsetDateTime)}. */
    interface DailyCount {
        LocalDate getDay();
        long getTotal();
    }
}
