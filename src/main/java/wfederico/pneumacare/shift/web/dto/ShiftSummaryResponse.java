package wfederico.pneumacare.shift.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.shift.domain.ShiftStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the shift history: the shift plus the counts that make it
 * meaningful at a glance (how long it ran, and how much clinical activity it
 * carried).
 *
 * @param durationMinutes elapsed minutes; for an OPEN shift this is measured
 *                        up to now, so the value keeps growing until it closes
 */
@Schema(description = "A shift with its duration and the clinical activity recorded during it.")
public record ShiftSummaryResponse(

        @Schema(description = "Shift UUID.") UUID id,
        @Schema(description = "ICU UUID this shift belongs to.") UUID icuId,
        @Schema(description = "UUID of the chief of guard who opened the shift.") UUID startedBy,
        @Schema(description = "Shift status.", example = "CLOSED") ShiftStatus status,
        @Schema(description = "UTC timestamp when the shift was opened.") OffsetDateTime startedAt,
        @Schema(description = "UTC timestamp when the shift was closed; null while OPEN.",
                nullable = true) OffsetDateTime endTime,
        @Schema(description = "Elapsed minutes (up to now while OPEN).") long durationMinutes,
        @Schema(description = "Handover notes recorded on this shift.") long handoverCount,
        @Schema(description = "Evaluations recorded during this shift.") long evaluationCount,
        @Schema(description = "Airway events recorded during this shift.") long airwayEventCount,
        @Schema(description = "Spontaneous breathing trials recorded during this shift.") long sbtCount) {
}
