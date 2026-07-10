package wfederico.pneumacare.clinical.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.clinical.domain.ConsultantGuidance;
import wfederico.pneumacare.clinical.domain.DrivingPressureBand;
import wfederico.pneumacare.clinical.domain.RiskMetric;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalCombinationRuleJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.ClinicalCombinationRuleRepository;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.MedicalReferenceRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, DB-backed clinical consultant.
 *
 * <p>Given an evaluation's computed metrics, composes concise, severity-ordered,
 * reference-grounded guidance in two layers:
 * <ol>
 *   <li><b>Cross-metric rules</b> ({@code clinical_combination_rule}) — whole-patient
 *       synthesis that fires only when several interpretation bands co-occur
 *       (e.g. a favorable RSBI alongside ARDS-range oxygenation). Composed first.</li>
 *   <li><b>Single-metric rows</b> ({@code medical_reference}) — the per-metric
 *       guidance for each concern band, appended after the cross-metric guidance.</li>
 * </ol>
 *
 * <p>A fully-normal evaluation with no matching rule yields {@link #SAFE_DEFAULT}.
 * No external service, no network call, no writes, no PII: input is
 * {@link VentilatorEvaluationResult} (computed values + interpretations) plus an
 * optional driving-pressure band.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalConsultantService {

    /** Returned when no metric or rule matches any reference entry. */
    static final String SAFE_DEFAULT = "insufficient reference data";

    /** Guidance is capped at this many sentences for conciseness. */
    private static final int MAX_SENTENCES = 4;

    /** Splits a {@code source_ref} that bundles several citations. */
    private static final String SOURCE_SEPARATOR = "; ";

    private final MedicalReferenceRepository referenceRepository;
    private final ClinicalCombinationRuleRepository combinationRuleRepository;

    /**
     * Composes reference-grounded guidance for an evaluation's computed metrics
     * without a driving-pressure signal.
     *
     * @param result computed RSBI/PaFi/Cstat values with their interpretations
     * @return severity-ordered guidance, or the safe default when nothing matches
     */
    @Transactional(readOnly = true)
    public ConsultantGuidance compose(VentilatorEvaluationResult result) {
        return compose(result, null);
    }

    /**
     * Composes reference-grounded guidance for an evaluation's computed metrics.
     *
     * @param result computed RSBI/PaFi/Cstat values with their interpretations
     * @param drivingPressure driving-pressure band, or {@code null} when the
     *                        plateau/PEEP needed to derive it is unavailable
     * @return cross-metric then single-metric guidance grounded in curated
     *         references, or the safe default when nothing matches
     */
    @Transactional(readOnly = true)
    public ConsultantGuidance compose(VentilatorEvaluationResult result, DrivingPressureBand drivingPressure) {
        List<String> guidanceSentences = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        // Layer 1 — cross-metric rules, highest priority first.
        combinationRuleRepository.findAll().stream()
                .filter(rule -> matches(rule, result, drivingPressure))
                .sorted(Comparator.comparingInt(ClinicalCombinationRuleJpaEntity::getPriority).reversed())
                .forEach(rule -> {
                    guidanceSentences.add(rule.getGuidanceText());
                    sources.add(rule.getSourceRef());
                });

        // Layer 2 — single-metric concern rows, highest priority first.
        List<MedicalReferenceJpaEntity> singleMetric = new ArrayList<>();
        referenceRepository.findByMetricAndBand(
                        RiskMetric.RSBI.name(), result.rsbi().interpretation().name())
                .ifPresent(singleMetric::add);
        referenceRepository.findByMetricAndBand(
                        RiskMetric.PAFI.name(), result.pafi().classification().name())
                .ifPresent(singleMetric::add);
        referenceRepository.findByMetricAndBand(
                        RiskMetric.CSTAT.name(), result.cstat().interpretation().name())
                .ifPresent(singleMetric::add);
        singleMetric.stream()
                .sorted(Comparator.comparingInt(MedicalReferenceJpaEntity::getPriority).reversed())
                .forEach(ref -> {
                    guidanceSentences.add(ref.getGuidanceText());
                    sources.add(ref.getSourceRef());
                });

        if (guidanceSentences.isEmpty()) {
            return new ConsultantGuidance(SAFE_DEFAULT, List.of());
        }

        List<String> chosen = guidanceSentences.stream().distinct().limit(MAX_SENTENCES).toList();
        List<String> citations = distinctCitations(sources, chosen, guidanceSentences);

        String text = String.join(" ", chosen) + " Ref: " + String.join(SOURCE_SEPARATOR, citations);
        return new ConsultantGuidance(text, citations);
    }

    /**
     * A rule fires only when every non-null band column contains this
     * evaluation's corresponding band. Each column is a comma-separated
     * allow-list; a {@code null} column is a wildcard.
     */
    private boolean matches(ClinicalCombinationRuleJpaEntity rule,
                            VentilatorEvaluationResult result,
                            DrivingPressureBand drivingPressure) {
        return bandAllowed(rule.getRsbiBand(), result.rsbi().interpretation().name())
                && bandAllowed(rule.getPafiBand(), result.pafi().classification().name())
                && bandAllowed(rule.getCstatBand(), result.cstat().interpretation().name())
                && bandAllowed(rule.getDpBand(), drivingPressure == null ? null : drivingPressure.name());
    }

    /**
     * {@code true} if the column is a wildcard (null/blank), or if it lists the
     * evaluation's band. A required column never matches a missing band.
     */
    private boolean bandAllowed(String allowList, String evaluationBand) {
        if (allowList == null || allowList.isBlank()) {
            return true;
        }
        if (evaluationBand == null) {
            return false;
        }
        return Arrays.stream(allowList.split(","))
                .map(String::trim)
                .anyMatch(evaluationBand::equals);
    }

    /**
     * Distinct citations across the chosen sentences, preserving order. A single
     * {@code source_ref} may bundle several references joined by {@code "; "};
     * those are split so shared references de-duplicate across layers.
     */
    private List<String> distinctCitations(List<String> sources, List<String> chosen, List<String> allSentences) {
        Set<String> citations = new LinkedHashSet<>();
        for (String sentence : chosen) {
            String source = sources.get(allSentences.indexOf(sentence));
            for (String ref : source.split(SOURCE_SEPARATOR)) {
                if (!ref.isBlank()) {
                    citations.add(ref.trim());
                }
            }
        }
        return List.copyOf(citations);
    }
}
