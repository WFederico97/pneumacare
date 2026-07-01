package wfederico.pneumacare.notification.application;

import wfederico.pneumacare.shared.event.PatientRiskEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound alert payload sent to the external notification channel (n8n).
 * Mapped from {@link PatientRiskEvent}; {@code bedLabel} may be null.
 */
public record AlertNotification(
        UUID patientId,
        UUID shiftId,
        String bedLabel,
        List<Metric> breachedMetrics,
        Instant timestamp
) {
    /** A single breached metric in the payload. */
    public record Metric(String metricName, double value) {}

    /** Builds the payload from a domain event, stamping the dispatch time. */
    public static AlertNotification from(PatientRiskEvent event, Instant timestamp) {
        List<Metric> metrics = event.breachedMetrics().stream()
                .map(m -> new Metric(m.metricName(), m.value()))
                .toList();
        return new AlertNotification(
                event.patientId(), event.shiftId(), event.bedLabel(), metrics, timestamp);
    }

    /**
     * Serialises the payload to the snake_case structure POSTed to n8n and stored
     * as the audit-log payload — the single source of truth for both. A
     * {@link LinkedHashMap} is used so {@code bed_label} can hold a null value and
     * key order is stable.
     */
    public Map<String, Object> toPayloadMap() {
        List<Map<String, Object>> metrics = breachedMetrics.stream()
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("metric_name", m.metricName());
                    entry.put("value", m.value());
                    return entry;
                })
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", patientId);
        body.put("shift_id", shiftId);
        body.put("bed_label", bedLabel);
        body.put("breached_metrics", metrics);
        body.put("timestamp", timestamp.toString());
        return body;
    }
}
