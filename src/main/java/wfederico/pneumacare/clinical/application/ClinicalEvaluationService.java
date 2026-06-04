package wfederico.pneumacare.clinical.application;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.web.dto.CstatRequest;
import wfederico.pneumacare.clinical.web.dto.CstatResponse;
import wfederico.pneumacare.clinical.web.dto.PafiRequest;
import wfederico.pneumacare.clinical.web.dto.PafiResponse;
import wfederico.pneumacare.clinical.web.dto.RsbiRequest;
import wfederico.pneumacare.clinical.web.dto.RsbiResponse;

/**
 * Application service for respiratory clinical index calculations.
 *
 * <p>Each public method is annotated with {@link Observed}, which causes
 * Spring's {@link io.micrometer.observation.ObservationRegistry} to:
 * <ol>
 *   <li>Create a child {@link io.opentelemetry.api.trace.Span} for the active
 *       HTTP trace, including the wall-clock duration of the calculation.</li>
 *   <li>Record a Micrometer {@link io.micrometer.core.instrument.Timer} whose
 *       OTLP export produces {@code clinical_rsbi_calculation_milliseconds_*}
 *       and {@code clinical_pafi_calculation_milliseconds_*} metrics in
 *       Grafana.</li>
 * </ol>
 *
 * <p><b>PII policy:</b> Only derived/computed clinical values (RSBI score,
 * interpretation) are written to span attributes.
 * The {@link wfederico.pneumacare.shared.telemetry.PiiSanitizingSpanExporter}
 * provides an additional defence-in-depth layer.
 *
 * <p><b>OTel agent note:</b> This application uses
 * {@code spring-boot-starter-opentelemetry} (Spring Boot 4), which provides
 * equivalent auto-instrumentation to the OTel Java Agent via Spring's native
 * Micrometer–OTel bridge. No {@code -javaagent} JVM argument is required.
 */
@Service
public class ClinicalEvaluationService {

    // ── RSBI ─────────────────────────────────────────────────────────────────

    /**
     * Calculates the Rapid Shallow Breathing Index (RSBI).
     *
     * <p>Formula: RSBI = Respiratory Rate (breaths/min) / Tidal Volume (L)
     *
     * <p>An {@link Observed} span named {@code clinical.rsbi.calculation} is
     * created for every invocation. The calculated score and interpretation are
     * added as <em>non-PII</em> span attributes.
     */
    @Observed(
            name            = "clinical.rsbi.calculation",
            contextualName  = "calculate-rsbi",
            lowCardinalityKeyValues = {"endpoint", "rsbi"}
    )
    public RsbiResponse calculateRsbi(RsbiRequest req) {
        double rsbi = ClinicalMathEngine.calculateRsbi(req.respiratoryRate(),req.tidalVolume());
        RsbiInterpretation interpretation = interpretRsbi(rsbi);
        // ── Safe span attributes — computed values only, no PII ──────────────
        Span current = Span.current();
        current.setAttribute("clinical.rsbi.value",          rsbi);
        current.setAttribute("clinical.rsbi.interpretation",  interpretation.name());
        current.setAttribute("clinical.rsbi.respiratory_rate", req.respiratoryRate());
        current.setAttribute("clinical.rsbi.tidal_volume",    req.tidalVolume());

        return new RsbiResponse(rsbi, interpretation);
    }

    // ── PaFi ─────────────────────────────────────────────────────────────────

    /**
     * Calculates the PaO₂/FiO₂ ratio (PaFi / P/F ratio).
     *
     * <p>Formula: PaFi = PaO₂ (mmHg) / FiO₂ (fraction)
     *
     * <p>Classified using the Berlin Definition of ARDS (2012).
     */
    @Observed(
            name            = "clinical.pafi.calculation",
            contextualName  = "calculate-pafi",
            lowCardinalityKeyValues = {"endpoint", "pafi"}
    )
    public PafiResponse calculatePafi(PafiRequest req) {
        double pafi = ClinicalMathEngine.calculatePafi(req.pao2(),req.fio2());
        PafiClassification classification = classifyPafi(pafi);

        // ── Safe span attributes — computed values only, no PII ──────────────
        Span current = Span.current();
        current.setAttribute("clinical.pafi.value",          pafi);
        current.setAttribute("clinical.pafi.classification",  classification.name());
        current.setAttribute("clinical.pafi.pao2",           req.pao2());
        current.setAttribute("clinical.pafi.fio2",           req.fio2());

        return new PafiResponse(pafi, classification);
    }

    @Observed(
            name            = "clinical.cstat.calculation",
            contextualName  = "calculate-cstat",
            lowCardinalityKeyValues = {"endpoint", "cstat"}
    )
    public CstatResponse calculateCstat(CstatRequest req) {
        double cstat = ClinicalMathEngine.calculateCstat(req.tidalVolume(),req.plateauPressure(), req.peepTotal());
        CstatInterpretation interpretation = interpretCstat(cstat);

        // ── Safe span attributes — computed values only, no PII ──────────────
        Span current = Span.current();
        current.setAttribute("clinical.cstat.value",          cstat);
        current.setAttribute("clinical.cstat.interpretation", interpretation.name());
        current.setAttribute("clinical.cstat.tidal_volume",    req.tidalVolume());
        current.setAttribute("clinical.cstat.plateau_pressure", req.plateauPressure());
        current.setAttribute("clinical.cstat.peep_total",      req.peepTotal());

        return new CstatResponse(cstat, interpretation);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static RsbiInterpretation interpretRsbi(double rsbi) {
        if (rsbi < 80)   return RsbiInterpretation.FAVORABLE;
        if (rsbi <= 105) return RsbiInterpretation.BORDERLINE;
        return RsbiInterpretation.UNFAVORABLE;
    }

    private static PafiClassification classifyPafi(double pafi) {
        if (pafi >= 400) return PafiClassification.NORMAL;
        if (pafi >= 300) return PafiClassification.AT_RISK;
        if (pafi >= 200) return PafiClassification.MILD_ARDS;
        if (pafi >= 100) return PafiClassification.MODERATE_ARDS;
        return PafiClassification.SEVERE_ARDS;
    }

    private static CstatInterpretation interpretCstat(double cstat) {
        if (cstat >= 100) return CstatInterpretation.HIGH;
        if (cstat >= 50)  return CstatInterpretation.NORMAL;
        return CstatInterpretation.LOW;
    }
}
