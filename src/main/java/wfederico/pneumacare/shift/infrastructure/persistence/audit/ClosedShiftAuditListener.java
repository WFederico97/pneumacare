package wfederico.pneumacare.shift.infrastructure.persistence.audit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;

import java.util.UUID;

/**
 * Detects writes that target a record belonging to a {@code CLOSED} shift and raises
 * a non-blocking audit alert (PNMC-134).
 *
 * <p>Registered against the Hibernate {@code SessionFactory} by
 * {@link HibernateAuditListenerRegistrar}. It runs in the same flush as the write, so
 * the Envers revision created for that write still lands in the audit trail — the
 * listener only observes; it never vetoes the persist.
 *
 * <h2>Detection rules</h2>
 * <ul>
 *   <li><b>{@link MedicalShiftJpaEntity} update:</b> if the <em>prior</em> {@code status}
 *       was already {@code CLOSED}, the row was edited after closure → alert. The
 *       legitimate close transition has a prior status of {@code OPEN}, so it is not
 *       flagged.</li>
 *   <li><b>{@link ShiftHandoverJpaEntity} insert/update:</b> if the owning shift is
 *       {@code CLOSED} → alert. Handovers are append-only and the service already blocks
 *       adds to closed shifts (409), so any such persisted write is anomalous.</li>
 * </ul>
 *
 * <p>On detection: a WARN log (no PII — only entity kind + UUID) and an increment of the
 * {@value #COUNTER} counter tagged {@code entity=medical_shift|handover}.
 */
@Slf4j
@Component
public class ClosedShiftAuditListener implements PostInsertEventListener, PostUpdateEventListener {

    /** Counter incremented once per detected retroactive write to a CLOSED shift. */
    static final String COUNTER = "shift.audit.closed_shift_write_total";

    private static final String STATUS_PROPERTY = "status";

    private final MeterRegistry meterRegistry;

    public ClosedShiftAuditListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (event.getEntity() instanceof ShiftHandoverJpaEntity handover
                && owningShiftClosed(event.getSession(), handover.getShiftId())) {
            alert("handover", handover.getId());
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Object entity = event.getEntity();
        if (entity instanceof MedicalShiftJpaEntity shift) {
            if (previousStatus(event) == ShiftStatus.CLOSED) {
                alert("medical_shift", shift.getId());
            }
        } else if (entity instanceof ShiftHandoverJpaEntity handover
                && owningShiftClosed(event.getSession(), handover.getShiftId())) {
            alert("handover", handover.getId());
        }
    }

    /** Reads the pre-update {@code status} from the event's old-state snapshot. */
    private ShiftStatus previousStatus(PostUpdateEvent event) {
        Object[] oldState = event.getOldState();
        if (oldState == null) {
            return null;
        }
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            if (STATUS_PROPERTY.equals(names[i])) {
                return (ShiftStatus) oldState[i];
            }
        }
        return null;
    }

    private boolean owningShiftClosed(SharedSessionContractImplementor session, UUID shiftId) {
        if (shiftId == null) {
            return false;
        }
        // For post-insert/post-update events the session is a full Hibernate Session;
        // get() returns the managed instance from the persistence context (no extra
        // query when the service already loaded the shift).
        MedicalShiftJpaEntity shift =
                ((Session) session).get(MedicalShiftJpaEntity.class, shiftId);
        return shift != null && shift.getStatus() == ShiftStatus.CLOSED;
    }

    private void alert(String entityTag, UUID id) {
        log.warn("Audit alert: write detected on a record under a CLOSED shift (entity={}, id={}).",
                entityTag, id);
        meterRegistry.counter(COUNTER, "entity", entityTag).increment();
    }

    /** Synchronous post-commit handling is not required; the alert fires during flush. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }
}
