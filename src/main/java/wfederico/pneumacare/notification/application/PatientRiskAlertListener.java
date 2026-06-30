package wfederico.pneumacare.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import wfederico.pneumacare.shared.event.PatientRiskEvent;

import java.time.Clock;

/**
 * Consumes {@link PatientRiskEvent} after the publishing transaction commits and
 * dispatches an alert to the external webhook on a dedicated async thread.
 * Fire-and-forget: failures are logged, never retried, and never propagated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatientRiskAlertListener {

    private final WebhookNotificationPort webhookNotificationPort;
    private final Clock clock;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPatientRiskEvent(PatientRiskEvent event) {
        AlertNotification notification = AlertNotification.from(event, clock.instant());
        try {
            webhookNotificationPort.send(notification);
            log.info("Patient risk alert DELIVERED: patientId={}, shiftId={}, metrics={}",
                    event.patientId(), event.shiftId(), event.breachedMetrics().size());
        } catch (RuntimeException ex) {
            log.warn("Patient risk alert FAILED (no retry): patientId={}, shiftId={}",
                    event.patientId(), event.shiftId(), ex);
        }
    }
}
