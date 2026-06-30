package wfederico.pneumacare.clinical.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RiskThresholdEvaluator}.
 *
 * <p>Risk thresholds (distinct from the clinical interpretation bands): a breach
 * occurs when RSBI &gt; 105, PaFi &lt; 150, or Cstat &lt; 30. Boundary values
 * (exactly 105 / 150 / 30) are safe and must NOT breach.
 */
class RiskThresholdEvaluatorTest {

    @Test
    @DisplayName("evaluate_allWithinSafeRanges_returnsNoBreaches")
    void evaluate_safeValues_empty() {
        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(80.0, 300.0, 45.0);
        assertThat(breaches).isEmpty();
    }

    @Test
    @DisplayName("evaluate_rsbiAboveThreshold_returnsRsbiBreach")
    void evaluate_rsbiBreach() {
        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(110.0, 300.0, 45.0);
        assertThat(breaches).containsExactly(new MetricBreach(RiskMetric.RSBI, 110.0));
    }

    @Test
    @DisplayName("evaluate_pafiBelowThreshold_returnsPafiBreach")
    void evaluate_pafiBreach() {
        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(80.0, 100.0, 45.0);
        assertThat(breaches).containsExactly(new MetricBreach(RiskMetric.PAFI, 100.0));
    }

    @Test
    @DisplayName("evaluate_cstatBelowThreshold_returnsCstatBreach")
    void evaluate_cstatBreach() {
        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(80.0, 300.0, 25.0);
        assertThat(breaches).containsExactly(new MetricBreach(RiskMetric.CSTAT, 25.0));
    }

    @Test
    @DisplayName("evaluate_multipleBreaches_returnsAllInStableOrder")
    void evaluate_multipleBreaches() {
        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(120.0, 100.0, 45.0);
        assertThat(breaches).containsExactly(
                new MetricBreach(RiskMetric.RSBI, 120.0),
                new MetricBreach(RiskMetric.PAFI, 100.0));
    }

    @Test
    @DisplayName("evaluate_exactlyOnThresholds_returnsNoBreaches")
    void evaluate_boundaryValues_safe() {
        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(105.0, 150.0, 30.0);
        assertThat(breaches).isEmpty();
    }
}
