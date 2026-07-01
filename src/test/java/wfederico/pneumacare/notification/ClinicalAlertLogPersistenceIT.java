package wfederico.pneumacare.notification;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.notification.domain.AlertDeliveryStatus;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogAdapter;
import wfederico.pneumacare.notification.infrastructure.persistence.ClinicalAlertLogRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Disabled by project convention (@SpringBootTest + Testcontainers). Run individually:
 * {@code ./mvnw.cmd test -Dtest=ClinicalAlertLogPersistenceIT}. Verifies the jsonb
 * round-trip and the guarded PENDING -> DELIVERED transition against a real database.
 */
@Disabled("Integration test — run individually with -Dtest=ClinicalAlertLogPersistenceIT")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClinicalAlertLogPersistenceIT {

    @Autowired
    private ClinicalAlertLogAdapter adapter;

    @Autowired
    private ClinicalAlertLogRepository repository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Test
    void recordPending_thenMarkDelivered_roundTripsPayloadAndStatus() {
        UUID eventId = UUID.randomUUID();
        adapter.recordPending(eventId, Map.of("patient_id", "p1", "value", 110.0));

        assertThat(repository.findByEventId(eventId)).isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.getStatus()).isEqualTo(AlertDeliveryStatus.PENDING);
                    assertThat(row.getPayload()).containsEntry("patient_id", "p1");
                });

        adapter.markDelivered(eventId);

        assertThat(repository.findByEventId(eventId))
                .get()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(AlertDeliveryStatus.DELIVERED));
    }
}
