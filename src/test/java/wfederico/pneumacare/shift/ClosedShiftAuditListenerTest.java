package wfederico.pneumacare.shift;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.Session;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.audit.ClosedShiftAuditListener;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClosedShiftAuditListener}: the detection rules for retroactive
 * writes to a {@code CLOSED} shift, and the {@code shift.audit.closed_shift_write_total}
 * counter increments (PNMC-134).
 */
class ClosedShiftAuditListenerTest {

    private static final String COUNTER = "shift.audit.closed_shift_write_total";
    private static final String[] SHIFT_PROPERTIES =
            {"icuId", "chiefUserId", "startTime", "endTime", "status"};
    private static final int STATUS_INDEX = 4;

    private SimpleMeterRegistry meterRegistry;
    private ClosedShiftAuditListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new ClosedShiftAuditListener(meterRegistry);
    }

    private double counter(String entityTag) {
        Counter c = meterRegistry.find(COUNTER).tag("entity", entityTag).counter();
        return c == null ? 0d : c.count();
    }

    private PostUpdateEvent shiftUpdateEvent(MedicalShiftJpaEntity shift, ShiftStatus previousStatus) {
        Object[] oldState = new Object[SHIFT_PROPERTIES.length];
        oldState[STATUS_INDEX] = previousStatus;

        EntityPersister persister = mock(EntityPersister.class);
        when(persister.getPropertyNames()).thenReturn(SHIFT_PROPERTIES);

        PostUpdateEvent event = mock(PostUpdateEvent.class);
        when(event.getEntity()).thenReturn(shift);
        when(event.getOldState()).thenReturn(oldState);
        when(event.getPersister()).thenReturn(persister);
        return event;
    }

    @Test
    @DisplayName("flags a shift update whose prior status was CLOSED")
    void shiftEditedAfterClosure_increments() {
        MedicalShiftJpaEntity shift = MedicalShiftJpaEntity.builder()
                .id(UUID.randomUUID()).status(ShiftStatus.CLOSED).build();

        listener.onPostUpdate(shiftUpdateEvent(shift, ShiftStatus.CLOSED));

        assertThat(counter("medical_shift")).isEqualTo(1d);
    }

    @Test
    @DisplayName("does not flag the legitimate OPEN -> CLOSED close transition")
    void legitimateCloseTransition_doesNotIncrement() {
        MedicalShiftJpaEntity shift = MedicalShiftJpaEntity.builder()
                .id(UUID.randomUUID()).status(ShiftStatus.CLOSED).build();

        listener.onPostUpdate(shiftUpdateEvent(shift, ShiftStatus.OPEN));

        assertThat(counter("medical_shift")).isZero();
    }

    @Test
    @DisplayName("flags a handover inserted under a CLOSED shift")
    void handoverUnderClosedShift_increments() {
        UUID shiftId = UUID.randomUUID();
        ShiftHandoverJpaEntity handover = ShiftHandoverJpaEntity.builder()
                .id(UUID.randomUUID()).shiftId(shiftId).build();
        MedicalShiftJpaEntity closedShift = MedicalShiftJpaEntity.builder()
                .id(shiftId).status(ShiftStatus.CLOSED).build();

        EventSource session = mock(EventSource.class);
        when(((Session) session).find(MedicalShiftJpaEntity.class, shiftId)).thenReturn(closedShift);

        PostInsertEvent event = mock(PostInsertEvent.class);
        when(event.getEntity()).thenReturn(handover);
        when(event.getSession()).thenReturn(session);

        listener.onPostInsert(event);

        assertThat(counter("handover")).isEqualTo(1d);
    }

    @Test
    @DisplayName("does not flag a handover inserted under an OPEN shift")
    void handoverUnderOpenShift_doesNotIncrement() {
        UUID shiftId = UUID.randomUUID();
        ShiftHandoverJpaEntity handover = ShiftHandoverJpaEntity.builder()
                .id(UUID.randomUUID()).shiftId(shiftId).build();
        MedicalShiftJpaEntity openShift = MedicalShiftJpaEntity.builder()
                .id(shiftId).status(ShiftStatus.OPEN).build();

        EventSource session = mock(EventSource.class);
        when(((Session) session).find(MedicalShiftJpaEntity.class, shiftId)).thenReturn(openShift);

        PostInsertEvent event = mock(PostInsertEvent.class);
        when(event.getEntity()).thenReturn(handover);
        when(event.getSession()).thenReturn(session);

        listener.onPostInsert(event);

        assertThat(counter("handover")).isZero();
    }
}
