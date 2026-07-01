package wfederico.pneumacare.notification.application;

import java.util.Map;
import java.util.UUID;

/**
 * Outbound port for auditing alert dispatch outcomes to {@code clinical_alerts_log}.
 * Each method is an independent unit of work: {@code recordPending} commits before
 * the webhook call; the terminal updates run afterwards. Implementations enforce the
 * only legal transition, PENDING → DELIVERED | FAILED.
 */
public interface AlertAuditPort {

    /** Inserts a PENDING row for {@code eventId} carrying the dispatched payload. */
    void recordPending(UUID eventId, Map<String, Object> payload);

    /** Transitions the PENDING row for {@code eventId} to DELIVERED. */
    void markDelivered(UUID eventId);

    /** Transitions the PENDING row for {@code eventId} to FAILED. */
    void markFailed(UUID eventId);
}
