package wfederico.pneumacare.inventory.web.dto;

import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VentilatorResponse(
        UUID id,
        String serialNumber,
        String brand,
        String modelName,
        UUID icuId,
        VentilatorStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** Must be called within a transaction: touches the lazy {@code model}. */
    public static VentilatorResponse from(PhysicalVentilatorJpaEntity entity) {
        return new VentilatorResponse(
                entity.getId(),
                entity.getSerialNumber(),
                entity.getModel().getBrand(),
                entity.getModel().getModel(),
                entity.getIcuId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
