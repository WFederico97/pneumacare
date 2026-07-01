package wfederico.pneumacare.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import wfederico.pneumacare.shared.event.PatientRiskEvent;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@link PatientRiskEvent} after the publishing transaction commits and
 * dispatches an alert to the external webhook on a dedicated async thread.
 * Persists an audit trail: a PENDING row before the call, then DELIVERED/FAILED.
 * Fire-and-forget — dispatch failures are logged, never retried, never propagated;
 * audit-store failures are logged and never break dispatch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientRiskAlertListener {

    private final WebhookNotificationPort webhookNotificationPort;
    private final AlertAuditPort alertAuditPort;
    private final Clock clock;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatientRiskEvent(PatientRiskEvent event) {
        UUID eventId = event.eventId();
        AlertNotification notification = AlertNotification.from(event, clock.instant());

        recordPending(eventId, notification.toPayloadMap());

        try {
            webhookNotificationPort.send(notification);
        } catch (RuntimeException ex) {
            markFailed(eventId);
            log.warn("Patient risk alert FAILED (no retry): eventId={}, patientId={}, shiftId={}",
                    eventId, event.patientId(), event.shiftId(), ex);
            return;
        }

        markDelivered(eventId);
        log.info("Patient risk alert DELIVERED: eventId={}, patientId={}, shiftId={}, metrics={}",
                eventId, event.patientId(), event.shiftId(), event.breachedMetrics().size());
    }

    private void recordPending(UUID eventId, Map<String, Object> payload) {
        try {
            alertAuditPort.recordPending(eventId, payload);
        } catch (RuntimeException ex) {
            log.warn("Alert audit PENDING write failed (dispatch continues): eventId={}", eventId, ex);
        }
    }

    private void markDelivered(UUID eventId) {
        try {
            alertAuditPort.markDelivered(eventId);
        } catch (RuntimeException ex) {
            log.warn("Alert audit DELIVERED update failed: eventId={}", eventId, ex);
        }
    }

    private void markFailed(UUID eventId) {
        try {
            alertAuditPort.markFailed(eventId);
        } catch (RuntimeException ex) {
            log.warn("Alert audit FAILED update failed: eventId={}", eventId, ex);
        }
    }
}
