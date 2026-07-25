package wfederico.pneumacare.clinical.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.clinical.domain.DrivingPressureBand;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalConsultantInsightJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalConsultantInsightRepository;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.clinical.web.dto.InsightResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cache-aside provider of clinical consultant guidance for a stored evaluation.
 *
 * <p>First read composes the guidance via {@link ClinicalConsultantService} from
 * the evaluation's persisted interpretation bands and stores it; later reads
 * return the stored copy. No recomputation from raw ventilator parameters, no
 * external call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalConsultantInsightService {

    private final ClinicalConsultantInsightRepository insightRepository;
    private final EvaluationRepository evaluationRepository;
    private final ClinicalConsultantService consultantService;

    /**
     * Returns the consultant insight for an evaluation, composing and persisting
     * it on first access.
     *
     * @param evaluationId the evaluation to explain
     * @return the insight text with a flag indicating cache hit vs fresh compose
     * @throws BusinessLayerException 404 if the evaluation does not exist
     */
    @Transactional
    public InsightResponse getOrCreate(UUID evaluationId) {
        var existing = insightRepository.findByEvaluationId(evaluationId);
        if (existing.isPresent()) {
            return new InsightResponse(evaluationId, existing.get().getInsightText(), true);
        }

        EvaluationJpaEntity evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BusinessLayerException(
                        "No se encontró la evaluación con id: " + evaluationId, HttpStatus.NOT_FOUND));

        String text = composeText(evaluation);

        ClinicalConsultantInsightJpaEntity insight = ClinicalConsultantInsightJpaEntity.builder()
                .evaluationId(evaluationId)
                .insightText(text)
                .build();

        try {
            // saveAndFlush so the unique(evaluation_id) constraint fires inside this block.
            ClinicalConsultantInsightJpaEntity saved = insightRepository.saveAndFlush(insight);
            return new InsightResponse(evaluationId, saved.getInsightText(), false);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request composed the same insight first — return its row.
            return insightRepository.findByEvaluationId(evaluationId)
                    .map(winner -> new InsightResponse(evaluationId, winner.getInsightText(), true))
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Composes guidance from the evaluation's persisted interpretation bands.
     * A stored evaluation normally carries all three bands; if any is missing we
     * fall back to the consultant's safe default rather than fail.
     */
    private String composeText(EvaluationJpaEntity e) {
        if (e.getRsbiInterpretation() == null
                || e.getPafiClassification() == null
                || e.getCstatInterpretation() == null) {
            log.warn("Evaluation {} is missing an interpretation band; returning safe default",
                    e.getId());
            return ClinicalConsultantService.SAFE_DEFAULT;
        }
        VentilatorEvaluationResult result = new VentilatorEvaluationResult(
                new RsbiResult(toDouble(e.getRsbiSnapshot()), e.getRsbiInterpretation()),
                new PafiResult(toDouble(e.getPafiSnapshot()), e.getPafiClassification()),
                new CstatResult(toDouble(e.getCstatSnapshot()), e.getCstatInterpretation()));
        return consultantService.compose(result, drivingPressureBand(e)).text();
    }

    /**
     * Derives the driving-pressure band (ΔP = Pplat − PEEP) when both pressures
     * are recorded and physiologically valid; {@code null} otherwise, so the
     * consultant simply skips driving-pressure rules.
     */
    private static DrivingPressureBand drivingPressureBand(EvaluationJpaEntity e) {
        if (e.getPplat() == null || e.getPeep() == null) {
            return null;
        }
        double drivingPressure = e.getPplat().subtract(e.getPeep()).doubleValue();
        if (drivingPressure <= 0) {
            return null;
        }
        return DrivingPressureBand.from(drivingPressure);
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
