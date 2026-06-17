package wfederico.pneumacare.timeline.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.timeline.domain.TimelineEntry;
import wfederico.pneumacare.timeline.domain.TimelineEventType;

import java.time.OffsetDateTime;

/**
 * One item in a patient's clinical timeline response (PNMC-133).
 *
 * <p>{@code payload} is the source context's own response record
 * ({@code EvaluationResponse} / {@code AirwayEventResponse} / {@code SbtResponse}),
 * selected by {@code type}. It is typed as {@link Object} because the three sources
 * contribute different shapes; Jackson serializes the concrete record transparently.
 */
@Schema(description = "A single clinical event in the patient's unified timeline.")
public record TimelineEntryResponse(

        @Schema(description = "Which source produced this event.", example = "AIRWAY")
        TimelineEventType type,

        @Schema(description = "When the event occurred (ISO-8601), used for ordering.",
                example = "2026-06-13T09:30:00Z")
        OffsetDateTime occurredAt,

        @Schema(description = "The source context's response payload for this event "
                + "(EvaluationResponse, AirwayEventResponse, or SbtResponse).")
        Object payload) {

    /** Maps a domain {@link TimelineEntry} to this response item. */
    public static TimelineEntryResponse from(TimelineEntry entry) {
        return new TimelineEntryResponse(entry.type(), entry.occurredAt(), entry.payload());
    }
}
