package wfederico.pneumacare.clinical.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    /** A patient's most recent evaluation, if any — used to derive the current alert state. */
    Optional<EvaluationJpaEntity> findFirstByPatientIdOrderByEvaluationTimeDesc(UUID patientId);

    /** Total evaluations recorded since the given instant (analytics). */
    long countByEvaluationTimeAfter(OffsetDateTime since);

    /** Evaluations since the given instant in a given RSBI band (analytics weaning). */
    long countByRsbiInterpretationAndEvaluationTimeAfter(RsbiInterpretation interpretation, OffsetDateTime since);

    /**
     * Patients whose most recent evaluation (recorded within the window) has a
     * high driving pressure (ΔP = Pplat − PEEP &gt; 15 cmH₂O). Drives the
     * driving-pressure watchlist: it counts each patient once, by their latest
     * reading, so a patient who was corrected back below threshold drops out.
     */
    @Query("""
           select count(distinct e.patientId) from EvaluationJpaEntity e
           where e.evaluationTime >= :since
             and (e.pplat - e.peep) > 15
             and e.evaluationTime = (
                 select max(e2.evaluationTime) from EvaluationJpaEntity e2
                 where e2.patientId = e.patientId)
           """)
    long countHighDrivingPressurePatients(OffsetDateTime since);

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
