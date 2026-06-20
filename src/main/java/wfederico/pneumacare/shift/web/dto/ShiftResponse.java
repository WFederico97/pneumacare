package wfederico.pneumacare.shift.web.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for shift endpoints. Represents the full shift resource.
 *
 * <p>{@code endTime} is null while the shift is {@link ShiftStatus#OPEN} and populated
 * once it transitions to {@link ShiftStatus#CLOSED}.
 *
 * <p>JSON uses camelCase (the project default). The {@code startedBy}/{@code startedAt}
 * names follow the ticket's API contract; they map from the entity's
 * {@code chiefUserId}/{@code startTime} fields.
 */
public record ShiftResponse(
        @Schema(description = "Shift UUID.", example = "bbbbbbbb-0000-0000-0000-000000000001")
        UUID id,

        @Schema(description = "ICU UUID this shift belongs to.",
                example = "cccccccc-0000-0000-0000-000000000001")
        UUID icuId,

        @Schema(description = "UUID of the chief of guard who opened the shift.",
                example = "eeeeeeee-0000-0000-0000-000000000001")
        UUID startedBy,

        @Schema(description = "Shift status.", example = "OPEN")
        ShiftStatus status,

        @Schema(description = "UTC timestamp when the shift was opened (ISO-8601).",
                example = "2026-06-13T08:00:00Z")
        OffsetDateTime startedAt,

        @Schema(description = "UTC timestamp when the shift was closed; null while OPEN.",
                example = "2026-06-13T20:00:00Z", nullable = true)
        OffsetDateTime endTime
) {
    /** Maps a {@link MedicalShiftJpaEntity} to this response DTO. */
    public static ShiftResponse from(MedicalShiftJpaEntity entity) {
        return new ShiftResponse(
                entity.getId(),
                entity.getIcuId(),
                entity.getChiefUserId(),
                entity.getStatus(),
                entity.getStartTime(),
                entity.getEndTime());
    }
}
