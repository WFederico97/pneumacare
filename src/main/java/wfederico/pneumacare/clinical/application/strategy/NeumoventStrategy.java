package wfederico.pneumacare.clinical.application.strategy;

import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.application.ClinicalMathEngine;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;

/**
 * Neumovent ventilator brand strategy.
 *
 * <p>Neumovent hardware reports tidal volume in <em>litres (L)</em>, which
 * differs from the TECME baseline that uses millilitres. The unit conversions
 * applied here are:
 *
 * <ul>
 *   <li><b>RSBI</b> — formula expects tidal volume in litres; Neumovent
 *       supplies it in litres already, so {@code tidalVolume} is passed
 *       directly with no conversion.</li>
 *   <li><b>Cstat</b> — formula expects tidal volume in millilitres; this
 *       strategy multiplies {@code tidalVolume} by {@value #L_TO_ML} before
 *       delegating to the math engine.</li>
 *   <li><b>PaFi</b> — no tidal-volume input; delegated unchanged.</li>
 * </ul>
 *
 * <p>All other validations (plateau pressure &gt; PEEP total, positive rates)
 * are enforced by {@link ClinicalMathEngine} and propagated as
 * {@link IllegalArgumentException}.
 */
@Component
public class NeumoventStrategy implements VentilatorStrategy {

    /** Conversion factor: litres to millilitres. */
    private static final double L_TO_ML = 1000.0;

    @Override
    public VentilatorEvaluationResult evaluate(VentilatorReading reading) {
        // Neumovent supplies tidalVolume in L — RSBI uses L directly, no conversion.
        double rsbi = ClinicalMathEngine.calculateRsbi(
                reading.respiratoryRate(),
                reading.tidalVolume());

        double pafi = ClinicalMathEngine.calculatePafi(
                reading.pao2(),
                reading.fio2());

        // Cstat formula requires mL; convert L → mL.
        double cstat = ClinicalMathEngine.calculateCstat(
                reading.tidalVolume() * L_TO_ML,
                reading.plateauPressure(),
                reading.peepTotal());

        return new VentilatorEvaluationResult(
                new RsbiResult(rsbi, RsbiInterpretation.from(rsbi)),
                new PafiResult(pafi, PafiClassification.from(pafi)),
                new CstatResult(cstat, CstatInterpretation.from(cstat))
        );
    }
}
