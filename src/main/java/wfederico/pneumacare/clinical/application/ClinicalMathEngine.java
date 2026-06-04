package wfederico.pneumacare.clinical.application;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.CSTAT_FORMULA_ERROR;

/**
 * Pure arithmetic engine for respiratory clinical index calculations.
 *
 * <p>This is a stateless utility class — all methods are {@code static} and
 * this class cannot be instantiated. It has no Spring dependencies and no
 * side effects, making it safe to call from any layer of the application.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Apply the exact clinical formula for each index.</li>
 *   <li>Guard against physically impossible input combinations
 *       (e.g. Pplat ≤ PEEPtotal in Cstat).</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> Clinical interpretation (FAVORABLE / AT_RISK / etc.)
 * is the responsibility of {@link ClinicalEvaluationService}, not this class.
 *
 * <p><b>Thread safety:</b> Fully thread-safe — no shared mutable state.
 *
 * <p><b>Performance:</b> Each method executes a fixed number of arithmetic
 * operations. Measured execution time is well under 1 ms per call; all three
 * methods satisfy the 10 ms performance budget defined in the project criteria.
 */
public final class ClinicalMathEngine {
    private ClinicalMathEngine() {};

    // ── RSBI ─────────────────────────────────────────────────────────────────

    /**
     * Calculates the Rapid Shallow Breathing Index (RSBI).
     *
     * <p><b>Formula:</b> RSBI = f / VT
     *
     * <p><b>Business rules:</b>
     * <ul>
     *   <li>f must be expressed in <em>breaths per minute (bpm)</em>.</li>
     *   <li>VT must be expressed in <em>litres (L)</em>, not mL.</li>
     *   <li>Global predictive cutoff: values < 105 indicate a high
     *       probability of successful weaning; values > 105 indicate
     *       respiratory distress and weaning intolerance.</li>
     * </ul>
     *
     * <p><b>Clinical interpretation</b> is performed by
     * {@link ClinicalEvaluationService#calculateRsbi}.
     *
     * @param respiratoryRate respiratory rate in breaths per minute (bpm)
     * @param tidalVolume     tidal volume in litres (L)
     * @return RSBI index expressed in breaths/min/L
     */
    public static double calculateRsbi(double respiratoryRate, double tidalVolume){
        return respiratoryRate / tidalVolume;
    }

    // ── PaFi ─────────────────────────────────────────────────────────────────

    /**
     * Calculates the PaO₂/FiO₂ ratio (PaFi, also known as P/F ratio).
     *
     * <p><b>Formula:</b> PaFi = PaO₂ / FiO₂
     *
     * <p><b>Business rules:</b>
     * <ul>
     *   <li>PaO₂ must be expressed in <em>mmHg</em>.</li>
     *   <li>FiO₂ must be a dimensionless decimal between 0.21 (room air)
     *       and 1.0 (100% oxygen).</li>
     *   <li>SBT safety threshold: a PaFi > 150 mmHg with PEEP ≤ 8 cmH₂O
     *       is required before initiating a Spontaneous Breathing Trial (SBT).</li>
     *   <li>ARDS severity follows the Berlin Definition (2012):
     *       ≥ 400 normal, 300–399 at risk, 200–299 mild,
     *       100–199 moderate, < 100 severe.</li>
     * </ul>
     *
     * <p><b>Clinical interpretation</b> is performed by
     * {@link ClinicalEvaluationService#calculatePafi}.
     *
     * @param pao2 arterial partial pressure of oxygen in mmHg
     * @param fio2 fraction of inspired oxygen as a decimal (0.21–1.0)
     * @return PaO₂/FiO₂ ratio in mmHg
     */
    public static double calculatePafi(double pao2, double fio2){
        return pao2/fio2;
    }

    // ── Cstat ─────────────────────────────────────────────────────────────────

    /**
     * Calculates the static respiratory system compliance (Cstat).
     *
     * <p><b>Formula:</b> Cstat = Vc / (Pplat − PEEPtotal)
     *
     * <p><b>Business rules:</b>
     * <ul>
     *   <li>Vc (tidal volume) must be expressed in <em>mL</em>.</li>
     *   <li>Pplat (plateau pressure) must be expressed in <em>cmH₂O</em>.</li>
     *   <li>PEEPtotal must account for <em>total PEEP</em>, including any
     *       intrinsic auto-PEEP present. Expressed in <em>cmH₂O</em>.</li>
     *   <li>Normal reference range for a ventilated patient: 50–100 mL/cmH₂O.
     *       Values below 50 indicate reduced compliance (stiff lung).
     *       Values above 100 may indicate over-distension.</li>
     * </ul>
     *
     * <p><b>Exception handling:</b> If {@code pplat} is less than or equal to
     * {@code peepTotal}, the denominator would be zero or negative, producing a
     * clinically meaningless result. This method throws
     * {@link IllegalArgumentException} in that case rather than returning
     * {@code Infinity} or a negative compliance value.
     *
     * <p><b>Clinical interpretation</b> is performed by
     * {@link ClinicalEvaluationService#calculateCstat}.
     *
     * @param tidalVolume tidal volume (Vc) in mL
     * @param pplat       plateau pressure in cmH₂O; must be greater than
     *                    {@code peepTotal}
     * @param peepTotal   total PEEP (extrinsic + intrinsic auto-PEEP) in cmH₂O
     * @return static compliance in mL/cmH₂O
     * @throws IllegalArgumentException if {@code pplat} ≤ {@code peepTotal}
     */
    public static double calculateCstat(double tidalVolume, double pplat, double peepTotal){
        if (pplat <= peepTotal){
            throw new IllegalArgumentException(
                    CSTAT_FORMULA_ERROR
            );
        }
        return tidalVolume/ (pplat - peepTotal);
    }
}
