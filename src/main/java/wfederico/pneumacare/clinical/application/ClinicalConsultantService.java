package wfederico.pneumacare.clinical.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.clinical.domain.ConsultantGuidance;
import wfederico.pneumacare.clinical.domain.RiskMetric;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic, DB-backed clinical consultant.
 *
 * <p>Given an evaluation's computed metrics, looks up the curated
 * {@code medical_reference} entry for each metric's interpretation band and
 * composes a concise, severity-ordered guidance string. Concern bands only:
 * a fully-normal evaluation matches nothing and yields {@link #SAFE_DEFAULT}.
 *
 * <p>No external service, no network call, no writes. Input is
 * {@link VentilatorEvaluationResult} (computed values + interpretations) — no
 * PII is read or emitted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalConsultantService {

    /** Returned when no metric matches any reference entry. */
    static final String SAFE_DEFAULT = "insufficient reference data";

    /** Guidance is capped at this many sentences for conciseness. */
    private static final int MAX_SENTENCES = 3;

    private final MedicalReferenceRepository referenceRepository;

    /**
     * Composes reference-grounded guidance for an evaluation's computed metrics.
     *
     * @param result computed RSBI/PaFi/Cstat values with their interpretations
     * @return severity-ordered guidance grounded in curated references, or the
     *         safe default when no metric band matches a reference entry
     */
    @Transactional(readOnly = true)
    public ConsultantGuidance compose(VentilatorEvaluationResult result) {
        List<MedicalReferenceJpaEntity> matches = new ArrayList<>();
        referenceRepository.findByMetricAndBand(
                        RiskMetric.RSBI.name(), result.rsbi().interpretation().name())
                .ifPresent(matches::add);
        referenceRepository.findByMetricAndBand(
                        RiskMetric.PAFI.name(), result.pafi().classification().name())
                .ifPresent(matches::add);
        referenceRepository.findByMetricAndBand(
                        RiskMetric.CSTAT.name(), result.cstat().interpretation().name())
                .ifPresent(matches::add);

        if (matches.isEmpty()) {
            return new ConsultantGuidance(SAFE_DEFAULT, List.of());
        }

        List<MedicalReferenceJpaEntity> top = matches.stream()
                .sorted(Comparator.comparingInt(MedicalReferenceJpaEntity::getPriority).reversed())
                .limit(MAX_SENTENCES)
                .toList();

        String guidance = top.stream()
                .map(MedicalReferenceJpaEntity::getGuidanceText)
                .collect(Collectors.joining(" "));

        List<String> sources = top.stream()
                .map(MedicalReferenceJpaEntity::getSourceRef)
                .distinct()
                .toList();

        String text = guidance + " Ref: " + String.join("; ", sources);
        return new ConsultantGuidance(text, sources);
    }
}
