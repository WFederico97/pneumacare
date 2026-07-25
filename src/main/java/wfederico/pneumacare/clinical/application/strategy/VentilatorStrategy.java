package wfederico.pneumacare.clinical.application.strategy;

import wfederico.pneumacare.clinical.domain.VentilatorParameterField;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;

import java.util.List;

/**
 * Strategy contract for brand-specific ventilator data adapters.
 *
 * <p>Each implementation maps a canonical {@link VentilatorReading} into a
 * {@link VentilatorEvaluationResult} containing the three respiratory indices
 * (RSBI, PaFi, Cstat) plus their clinical interpretations.
 *
 * <p>Implementations are responsible for any unit conversions or
 * sensor-specific adjustments their brand requires before delegating to
 * {@link wfederico.pneumacare.clinical.application.ClinicalMathEngine}.
 *
 * <p>Resolved at runtime by {@link VentilatorFactory} based on the brand
 * string supplied by the caller.
 *
 * <p>Known implementations:
 * <ul>
 *   <li>{@link TecmeStrategy} — TECME (baseline; tidal volume in mL)</li>
 *   <li>{@link NeumoventStrategy} — Neumovent (tidal volume in L)</li>
 * </ul>
 */
public interface VentilatorStrategy {

    /**
     * Evaluates the full set of respiratory indices for the given reading.
     *
     * @param reading canonical clinical inputs for a single ventilator evaluation
     * @return aggregated result containing RSBI, PaFi, and Cstat with interpretations
     * @throws IllegalArgumentException if any input violates formula preconditions
     *         (e.g., plateau pressure &le; PEEP total)
     */
    VentilatorEvaluationResult evaluate(VentilatorReading reading);

    /**
     * The brand-specific extended parameters this ventilator exposes beyond the
     * six universal fields (f, Vt, FiO₂, PaO₂, PEEP, Pplat). Drives the dynamic
     * section of the evaluation form. Defaults to none.
     *
     * @return ordered field descriptors; empty when the brand adds no extra inputs
     */
    default List<VentilatorParameterField> extendedParameters() {
        return List.of();
    }
}
