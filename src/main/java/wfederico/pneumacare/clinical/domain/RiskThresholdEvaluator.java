package wfederico.pneumacare.clinical.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, stateless evaluation of the critical risk thresholds for a single
 * ventilator evaluation.
 *
 * <p>These thresholds are deliberately distinct from the clinical interpretation
 * bands in {@link RsbiInterpretation} / {@link PafiClassification} /
 * {@link CstatInterpretation}: a metric breaches when
 * <ul>
 *   <li>RSBI &gt; 105 (weaning intolerance), or</li>
 *   <li>PaFi &lt; 150 (below the SBT safety floor), or</li>
 *   <li>Cstat &lt; 30 (severely reduced compliance).</li>
 * </ul>
 * Boundary values (exactly 105 / 150 / 30) are safe and do not breach.
 */
public final class RiskThresholdEvaluator {

    static final double RSBI_MAX_SAFE  = 105.0; // breach when value > 105
    static final double PAFI_MIN_SAFE  = 150.0; // breach when value < 150
    static final double CSTAT_MIN_SAFE = 30.0;  // breach when value < 30

    private RiskThresholdEvaluator() {}

    /**
     * Evaluates the three calculated indices against the critical thresholds.
     *
     * @return breaches in stable order (RSBI, PAFI, CSTAT); empty if none breached
     */
    public static List<MetricBreach> evaluate(double rsbi, double pafi, double cstat) {
        List<MetricBreach> breaches = new ArrayList<>();
        if (rsbi > RSBI_MAX_SAFE)   breaches.add(new MetricBreach(RiskMetric.RSBI, rsbi));
        if (pafi < PAFI_MIN_SAFE)   breaches.add(new MetricBreach(RiskMetric.PAFI, pafi));
        if (cstat < CSTAT_MIN_SAFE) breaches.add(new MetricBreach(RiskMetric.CSTAT, cstat));
        return List.copyOf(breaches);
    }
}
