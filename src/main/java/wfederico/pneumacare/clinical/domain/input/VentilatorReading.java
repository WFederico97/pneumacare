package wfederico.pneumacare.clinical.domain.input;

/**
 * Canonical clinical inputs for a single ventilator evaluation.
 *
 * <p>All fields are {@code double} primitives — domain values are never
 * {@code null} because validation is performed at the HTTP boundary by the
 * request DTOs before data reaches the domain layer.
 *
 * <p>Strategies are free to apply brand-specific unit conversions before
 * delegating to
 * {@link wfederico.pneumacare.clinical.application.ClinicalMathEngine}.
 * See each strategy's class-level Javadoc for the conversions it performs.
 *
 * @param respiratoryRate breathing frequency, in breaths per minute
 * @param tidalVolume     volume of a single breath, in millilitres (mL)
 * @param pao2            arterial oxygen partial pressure, in millimetres
 *                        of mercury (mmHg)
 * @param fio2            fraction of inspired oxygen (decimal, 0.21 – 1.00)
 * @param plateauPressure airway plateau pressure, in centimetres of water
 *                        (cmH₂O); must be strictly greater than
 *                        {@code peepTotal}
 * @param peepTotal       total positive end-expiratory pressure, in
 *                        centimetres of water (cmH₂O)
 */
public record VentilatorReading(
        double respiratoryRate,
        double tidalVolume,
        double pao2,
        double fio2,
        double plateauPressure,
        double peepTotal) {

}
