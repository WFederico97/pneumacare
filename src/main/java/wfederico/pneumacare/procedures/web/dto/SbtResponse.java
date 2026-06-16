package wfederico.pneumacare.procedures.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for SBT endpoints.
 *
 * <p>{@code recordedAt} is the persistence timestamp (the ticket's
 * {@code recorded_at}), taken from the audited {@code created_at} column.
 */
public record SbtResponse(

        @Schema(description = "SBT UUID.", example = "ffffffff-0000-0000-0000-000000000001")
        UUID id,

        @Schema(description = "UUID of the patient.", example = "aaaaaaaa-0000-0000-0000-000000000001")
        UUID patientId,

        @Schema(description = "UUID of the OPEN shift the trial was recorded under.",
                example = "bbbbbbbb-0000-0000-0000-000000000001")
        UUID shiftId,

        @Schema(description = "Trial duration in minutes.", example = "30")
        Integer durationMinutes,

        @Schema(description = "Trial outcome.", example = "SUCCESS")
        ToleranceResult toleranceResult,

        @Schema(description = "UUID of the user who recorded the trial (performed_by).",
                example = "eeeeeeee-0000-0000-0000-000000000001")
        UUID performedBy,

        @Schema(description = "UTC timestamp when the trial was recorded (ISO-8601).",
                example = "2026-06-13T10:15:00Z")
        OffsetDateTime recordedAt
) {
    /** Maps an {@link SbtJpaEntity} to this response DTO. */
    public static SbtResponse from(SbtJpaEntity entity) {
        return new SbtResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getShiftId(),
                entity.getDurationMinutes(),
                entity.getToleranceResult(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
