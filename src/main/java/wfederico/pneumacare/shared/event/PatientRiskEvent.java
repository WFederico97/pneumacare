package wfederico.pneumacare.shared.event;

import java.util.List;
import java.util.UUID;

/**
 * Internal domain event published when a persisted ventilator evaluation breaches
 * one or more critical risk thresholds.
 *
 * <p>Published in-process by the {@code clinical} context via Spring's
 * {@code ApplicationEventPublisher} and consumed by the notification pipeline
 * (PNMC-99) with a {@code @TransactionalEventListener}. It is an internal domain
 * event, not an {@link EventPublisherPort} integration event, so delivery does
 * not depend on {@code app.kafka.enabled}. Carries only identifiers and computed
 * values — no PII. {@code bedLabel} may be {@code null} when the patient has no
 * bed currently assigned.
 *
 * @param eventId         unique id for this alert event; the audit-log correlation key
 * @param patientId       the evaluated patient (raw UUID, cross-context)
 * @param shiftId         the medical shift the evaluation belongs to
 * @param bedLabel        human-readable bed identifier (e.g. "Cama 3"), or null
 * @param breachedMetrics one entry per metric that crossed its threshold
 */
public record PatientRiskEvent(
        UUID eventId,
        UUID patientId,
        UUID shiftId,
        String bedLabel,
        List<BreachedMetric> breachedMetrics
) {
    /** A single breached metric in the event payload. */
    public record BreachedMetric(String metricName, double value) {}
}
