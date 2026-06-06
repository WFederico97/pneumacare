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
 * Baseline TECME ventilator brand strategy.
 *
 * <p>Treats the {@link VentilatorReading} fields as canonical TECME values and
 * delegates index calculation to {@link ClinicalMathEngine}. The only
 * brand-specific adaptation performed here is the tidal-volume unit conversion
 * required by the RSBI formula:
 *
 * <ul>
 *   <li><b>RSBI</b> — formula expects tidal volume in <em>litres</em>; this
 *       strategy divides {@code tidalVolume} (mL) by {@value #ML_PER_LITER}.</li>
 *   <li><b>Cstat</b> — formula expects tidal volume in <em>millilitres</em>;
 *       no conversion applied.</li>
 *   <li><b>PaFi</b> — no tidal-volume input; delegated unchanged.</li>
 * </ul>
 *
 * <p>Future brand strategies (Drager, Hamilton, …) may override these
 * conversions or add their own (e.g., temperature compensation, pressure offsets).
 */
@Component
public class TecmeStrategy implements VentilatorStrategy {

    private static final double ML_PER_LITER = 1000.0;

    @Override
    public VentilatorEvaluationResult evaluate(VentilatorReading reading) {
        double rsbi = ClinicalMathEngine.calculateRsbi(reading.respiratoryRate(), reading.tidalVolume() / ML_PER_LITER);
        double pafi = ClinicalMathEngine.calculatePafi(reading.pao2(), reading.fio2());
        double cstat = ClinicalMathEngine.calculateCstat(reading.tidalVolume(), reading.plateauPressure(), reading.peepTotal());

        return new VentilatorEvaluationResult(
                new RsbiResult(rsbi, RsbiInterpretation.from(rsbi)),
                new PafiResult(pafi, PafiClassification.from(pafi)),
                new CstatResult(cstat, CstatInterpretation.from(cstat))
        );
    }
}
