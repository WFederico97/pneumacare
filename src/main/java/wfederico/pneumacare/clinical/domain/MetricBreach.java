package wfederico.pneumacare.clinical.domain;

/**
 * A single risk-threshold breach: which metric crossed its critical threshold
 * and the calculated value that breached it.
 */
public record MetricBreach(RiskMetric metric, double value) {}
