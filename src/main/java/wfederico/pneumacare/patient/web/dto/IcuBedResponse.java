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
        BedStatus status,

        @Schema(
                description = "UUID of the admitted patient occupying this bed, null when not OCCUPIED.",
                example = "aaaaaaaa-0000-0000-0000-000000000001",
                nullable = true)
        UUID patientId,

        @Schema(
                description = "True when the occupying patient's latest evaluation tripped a clinical " +
                        "threshold. Drives the pulsating critical-alert styling on the bed grid.",
                example = "false")
        boolean criticalAlert) {

    /** Maps a {@link IcuBedJpaEntity} to this DTO with no patient context (AVAILABLE beds). */
    public static IcuBedResponse from(IcuBedJpaEntity entity) {
        return new IcuBedResponse(
                entity.getId(),
                entity.getBedNumber(),
                entity.getStatus(),
                null,
                false);
    }

    /** Maps a {@link IcuBedJpaEntity} to this DTO enriched with the admitted patient UUID. */
    public static IcuBedResponse from(IcuBedJpaEntity entity, UUID patientId) {
        return new IcuBedResponse(
                entity.getId(),
                entity.getBedNumber(),
                entity.getStatus(),
                patientId,
                false);
    }

    /** Maps a {@link IcuBedJpaEntity} to this DTO with patient context and current alert state. */
    public static IcuBedResponse from(IcuBedJpaEntity entity, UUID patientId, boolean criticalAlert) {
        return new IcuBedResponse(
                entity.getId(),
                entity.getBedNumber(),
                entity.getStatus(),
                patientId,
                criticalAlert);
    }
}

