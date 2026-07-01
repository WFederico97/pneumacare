package wfederico.pneumacare.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import wfederico.pneumacare.notification.domain.AlertDeliveryStatus;
import wfederico.pneumacare.shared.data.EntityBase;

import java.util.Map;
import java.util.UUID;

/**
 * Audit record for one patient-risk alert dispatch. One row per
 * {@code PatientRiskEvent}, keyed by {@code eventId}. Written PENDING before the
 * webhook call and updated to DELIVERED/FAILED after. {@code payload} is the exact
 * snake_case body POSTed to n8n. Extends {@link EntityBase} for created_at/updated_at.
 */
@Entity
@Table(name = "clinical_alerts_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalAlertLogJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Correlation key from {@code PatientRiskEvent.eventId}; unique per alert. */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    /** Exact snake_case payload POSTed to n8n. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false, updatable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AlertDeliveryStatus status;
}
