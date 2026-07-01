package wfederico.pneumacare.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wfederico.pneumacare.notification.domain.AlertDeliveryStatus;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogAdapter;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogJpaEntity;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalAlertLogAdapterTest {

    private static final UUID EVENT = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Mock
    private ClinicalAlertLogRepository repository;

    private ClinicalAlertLogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ClinicalAlertLogAdapter(repository);
    }

    @Test
    void recordPending_savesPendingRowWithPayload() {
        adapter.recordPending(EVENT, Map.of("patient_id", "p1"));

        ArgumentCaptor<ClinicalAlertLogJpaEntity> captor =
                ArgumentCaptor.forClass(ClinicalAlertLogJpaEntity.class);
        verify(repository).save(captor.capture());
        ClinicalAlertLogJpaEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(EVENT);
        assertThat(saved.getStatus()).isEqualTo(AlertDeliveryStatus.PENDING);
        assertThat(saved.getPayload()).containsEntry("patient_id", "p1");
    }

    @Test
    void markDelivered_pendingRow_transitionsToDelivered() {
        ClinicalAlertLogJpaEntity row = ClinicalAlertLogJpaEntity.builder()
                .eventId(EVENT).status(AlertDeliveryStatus.PENDING).build();
        when(repository.findByEventId(EVENT)).thenReturn(Optional.of(row));

        adapter.markDelivered(EVENT);

        assertThat(row.getStatus()).isEqualTo(AlertDeliveryStatus.DELIVERED);
    }

    @Test
    void markFailed_pendingRow_transitionsToFailed() {
        ClinicalAlertLogJpaEntity row = ClinicalAlertLogJpaEntity.builder()
                .eventId(EVENT).status(AlertDeliveryStatus.PENDING).build();
        when(repository.findByEventId(EVENT)).thenReturn(Optional.of(row));

        adapter.markFailed(EVENT);

        assertThat(row.getStatus()).isEqualTo(AlertDeliveryStatus.FAILED);
    }

    @Test
    void markDelivered_alreadyTerminal_isNoOp() {
        ClinicalAlertLogJpaEntity row = ClinicalAlertLogJpaEntity.builder()
                .eventId(EVENT).status(AlertDeliveryStatus.FAILED).build();
        when(repository.findByEventId(EVENT)).thenReturn(Optional.of(row));

        adapter.markDelivered(EVENT);

        assertThat(row.getStatus()).isEqualTo(AlertDeliveryStatus.FAILED);
    }

    @Test
    void markDelivered_missingRow_isNoOp() {
        when(repository.findByEventId(EVENT)).thenReturn(Optional.empty());

        assertThatCode(() -> adapter.markDelivered(EVENT)).doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }
}
