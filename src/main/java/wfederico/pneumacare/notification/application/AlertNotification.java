package wfederico.pneumacare.notification.application;

import wfederico.pneumacare.shared.event.PatientRiskEvent;

import java.time.Instant;
import java.util.List;
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
}
