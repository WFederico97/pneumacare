package wfederico.pneumacare.clinical.application.strategy;

import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.application.ClinicalMathEngine;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.VentilatorParameterField;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;

import java.util.List;

/**
 * Neumovent ventilator brand strategy.
 *
 * <p>Receives the canonical {@link VentilatorReading} where {@code tidalVolume}
 * is expressed in <strong>millilitres (mL)</strong> — the same unit convention
 * used by every other brand strategy. The HTTP boundary
 * ({@link wfederico.pneumacare.clinical.web.dto.CreateEvaluationRequest})
 * always carries {@code vt} in mL regardless of brand, so no per-brand unit
 * translation happens in the application service.
 *
 * <p><b>Unit handling:</b>
 * <ul>
 *   <li><b>RSBI</b> — formula expects tidal volume in <em>litres</em>; this
 *       strategy divides {@code tidalVolume} (mL) by {@value #ML_PER_LITER}.</li>
 *   <li><b>Cstat</b> — formula expects tidal volume in <em>millilitres</em>;
 *       no conversion applied.</li>
 *   <li><b>PaFi</b> — no tidal-volume input; delegated unchanged.</li>
 * </ul>
 *
 * <p><b>Why this currently mirrors {@link TecmeStrategy}:</b> the application
 * accepts manual data entry in mL for both brands, so the unit-conversion path
 * is the same. Brand-specific divergence is expected to appear once
 * {@code extendedParameters} processing differs (e.g., {@code inspTime} for
 * Neumovent vs {@code triggerFlow} for TECME), or when real hardware
 * integrations introduce sensor-specific corrections.
 *
 * <p>All other validations (plateau pressure &gt; PEEP total, positive rates)
 * are enforced by {@link ClinicalMathEngine} and propagated as
 * {@link IllegalArgumentException}.
 */
@Component
public class NeumoventStrategy implements VentilatorStrategy {

    /** Conversion factor: millilitres per litre. */
    private static final double ML_PER_LITER = 1000.0;

    @Override
    public VentilatorEvaluationResult evaluate(VentilatorReading reading) {
        // RSBI formula expects tidal volume in L; canonical reading is mL.
        double rsbi = ClinicalMathEngine.calculateRsbi(
                reading.respiratoryRate(),
                reading.tidalVolume() / ML_PER_LITER);

        double pafi = ClinicalMathEngine.calculatePafi(
                reading.pao2(),
                reading.fio2());

        // Cstat formula expects tidal volume in mL; canonical reading is mL.
        double cstat = ClinicalMathEngine.calculateCstat(
                reading.tidalVolume(),
                reading.plateauPressure(),
                reading.peepTotal());

        return new VentilatorEvaluationResult(
                new RsbiResult(rsbi, RsbiInterpretation.from(rsbi)),
                new PafiResult(pafi, PafiClassification.from(pafi)),
                new CstatResult(cstat, CstatInterpretation.from(cstat))
        );
    }

    @Override
    public List<VentilatorParameterField> extendedParameters() {
        return List.of(
                new VentilatorParameterField(
                        "inspTime", "Tiempo inspiratorio", "s", "number", 0.1, 5, 0.1, true));
    }
}
