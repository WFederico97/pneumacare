package wfederico.pneumacare.clinical.application;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;
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
 * interpretation) are written to span attributes. Patient identifiers such as
 * {@code nationalId} are <em>never</em> written to spans. The
 * {@link wfederico.pneumacare.shared.telemetry.PiiSanitizingSpanExporter}
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
        double rsbi = req.respiratoryRate() / req.tidalVolume();
        String interpretation = interpretRsbi(rsbi);

        // ── Safe span attributes — computed values only, no PII ──────────────
        Span current = Span.current();
        current.setAttribute("clinical.rsbi.value",          rsbi);
        current.setAttribute("clinical.rsbi.interpretation",  interpretation);
        current.setAttribute("clinical.rsbi.respiratory_rate", req.respiratoryRate());
        current.setAttribute("clinical.rsbi.tidal_volume",    req.tidalVolume());
        // NOTE: req.nationalId() is intentionally NOT added here. Adding PII to
        // span attributes would violate Law 25.326. The PiiSanitizingSpanExporter
        // is a safety net, but the primary control is not writing PII in the first place.

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
        double pafi = req.pao2() / req.fio2();
        String classification = classifyPafi(pafi);

        // ── Safe span attributes — computed values only, no PII ──────────────
        Span current = Span.current();
        current.setAttribute("clinical.pafi.value",          pafi);
        current.setAttribute("clinical.pafi.classification",  classification);
        current.setAttribute("clinical.pafi.pao2",           req.pao2());
        current.setAttribute("clinical.pafi.fio2",           req.fio2());

        return new PafiResponse(pafi, classification);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String interpretRsbi(double rsbi) {
        if (rsbi < 80)  return "FAVORABLE";
        if (rsbi <= 105) return "BORDERLINE";
        return "UNFAVORABLE";
    }

    private static String classifyPafi(double pafi) {
        if (pafi >= 400) return "NORMAL";
        if (pafi >= 300) return "AT_RISK";
        if (pafi >= 200) return "MILD_ARDS";
        if (pafi >= 100) return "MODERATE_ARDS";
        return "SEVERE_ARDS";
    }
}
