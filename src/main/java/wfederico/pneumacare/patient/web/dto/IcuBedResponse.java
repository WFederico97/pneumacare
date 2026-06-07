package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;

public record IcuBedResponse(
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
                entity.getBedNumber(),
                entity.getStatus());
    }
}
