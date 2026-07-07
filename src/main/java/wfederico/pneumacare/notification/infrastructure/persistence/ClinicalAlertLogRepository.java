package wfederico.pneumacare.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@code clinical_alerts_log}. */
public interface ClinicalAlertLogRepository extends JpaRepository<ClinicalAlertLogJpaEntity, UUID> {

    /** Single-row lookup by the unique correlation key (the terminal-update path). */
    Optional<ClinicalAlertLogJpaEntity> findByEventId(UUID eventId);

    /** Count of alerts logged since the given instant (analytics alert frequency). */
    long countByCreatedAtAfter(OffsetDateTime since);
}
