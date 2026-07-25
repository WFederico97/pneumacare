package wfederico.pneumacare.patient.application;

import wfederico.pneumacare.patient.domain.Disposition;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Internal domain event published in-JVM when an episode closes. Consumed (in
 * the future) by notification/SLA listeners via {@code @TransactionalEventListener}
 * — same-deployment event, so ApplicationEventPublisher per the AGENTS.md rule,
 * not Kafka.
 */
public record PatientDischargedEvent(UUID patientId, Disposition disposition, OffsetDateTime dischargeDate) {
}
