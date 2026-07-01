package wfederico.pneumacare.notification.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.notification.application.AlertAuditPort;
import wfederico.pneumacare.notification.domain.AlertDeliveryStatus;

import java.util.Map;
import java.util.UUID;

/**
 * JPA adapter persisting alert-dispatch outcomes. Invoked from the async dispatch
 * thread (no ambient transaction), so each method is its own {@link Transactional}
 * unit — {@code recordPending} commits before the webhook call. The terminal updates
 * mutate the row only while it is still PENDING, so PENDING → DELIVERED | FAILED is
 * the only transition that can ever be written.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClinicalAlertLogAdapter implements AlertAuditPort {

    private final ClinicalAlertLogRepository repository;

    @Override
    @Transactional
    public void recordPending(UUID eventId, Map<String, Object> payload) {
        ClinicalAlertLogJpaEntity row = ClinicalAlertLogJpaEntity.builder()
                .eventId(eventId)
                .payload(payload)
                .status(AlertDeliveryStatus.PENDING)
                .build();
        repository.save(row);
    }

    @Override
    @Transactional
    public void markDelivered(UUID eventId) {
        transition(eventId, AlertDeliveryStatus.DELIVERED);
    }

    @Override
    @Transactional
    public void markFailed(UUID eventId) {
        transition(eventId, AlertDeliveryStatus.FAILED);
    }

    /** Applies the terminal status only if the row is still PENDING (dirty-checking flush). */
    private void transition(UUID eventId, AlertDeliveryStatus terminal) {
        repository.findByEventId(eventId).ifPresentOrElse(row -> {
            if (row.getStatus() == AlertDeliveryStatus.PENDING) {
                row.setStatus(terminal);
            } else {
                log.warn("Alert audit transition skipped: eventId={} already {} (wanted {})",
                        eventId, row.getStatus(), terminal);
            }
        }, () -> log.warn("Alert audit transition skipped: no PENDING row for eventId={} (wanted {})",
                eventId, terminal));
    }
}
