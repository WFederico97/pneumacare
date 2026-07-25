package wfederico.pneumacare.inventory.web.dto;

import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.AssetAssignmentJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetAssignmentResponse(
        UUID id,
        UUID ventilatorId,
        UUID patientId,
        VentilatorStatus status,
        OffsetDateTime assignedAt,
        OffsetDateTime releasedAt
) {
    /** {@code status} is the ventilator's status after the operation. */
    public static AssetAssignmentResponse from(AssetAssignmentJpaEntity entity, VentilatorStatus status) {
        return new AssetAssignmentResponse(
                entity.getId(),
                entity.getVentilatorId(),
                entity.getPatientId(),
                status,
                entity.getAssignedAt(),
                entity.getReleasedAt());
    }
}
