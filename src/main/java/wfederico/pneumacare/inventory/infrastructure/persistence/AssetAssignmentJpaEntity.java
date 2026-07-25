package wfederico.pneumacare.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import wfederico.pneumacare.shared.data.EntityBase;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code asset_assignments} history table.
 *
 * <p>Thin link record: {@code ventilatorId} and {@code patientId} are raw UUIDs
 * (the patient belongs to another context; the ventilator is loaded separately
 * to transition its status). Active assignment has {@code releasedAt == null}.
 */
@Entity
@Table(name = "asset_assignments")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AssetAssignmentJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "ventilator_id", nullable = false)
    private UUID ventilatorId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;
}
