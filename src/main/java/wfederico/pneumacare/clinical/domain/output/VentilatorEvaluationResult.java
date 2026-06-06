package wfederico.pneumacare.clinical.domain.output;

import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;

/**
 * Aggregated result of a single ventilator evaluation across the three
 * respiratory indices.
 *
 * <p>Each nested sub-record bundles a calculated numeric value with its
 * enumerated clinical interpretation, so callers can render both for the
 * clinician without re-running classification logic.
 *
 * @param rsbi  RSBI value and weaning outcome interpretation
 * @param pafi  PaO₂/FiO₂ ratio value and Berlin Definition ARDS classification
 * @param cstat static lung compliance value and interpretation
 */
public record VentilatorEvaluationResult(
        RsbiResult rsbi,
        PafiResult pafi,
        CstatResult cstat
) {
    /** RSBI value (breaths/min/L) with weaning outcome interpretation. */
    public record RsbiResult(double value, RsbiInterpretation interpretation) {}

    /** PaO₂/FiO₂ ratio value with Berlin Definition ARDS classification. */
    public record PafiResult(double value, PafiClassification classification) {}

    /** Static lung compliance value (mL/cmH₂O) with interpretation. */
    public record CstatResult(double value, CstatInterpretation interpretation) {}
}
