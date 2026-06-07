package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;

import java.util.UUID;

public record IcuBedResponse(
        @Schema(
                description = "Bed UUID",
                example = "dddddddd-0000-0000-0000-000000000001")
        UUID bedId,

        @Schema(
                description = "Bed number",
                example = "BED-1")
        String bedNumber,

        @Schema(
                description = "Bed status.",
                example = "AVAILABLE")
        BedStatus status) {
    /** Maps a {@link IcuBedJpaEntity} to this DTO. */
    public static IcuBedResponse from(IcuBedJpaEntity entity) {
        return new IcuBedResponse(
                entity.getId(),
                entity.getBedNumber(),
                entity.getStatus());
    }
}
