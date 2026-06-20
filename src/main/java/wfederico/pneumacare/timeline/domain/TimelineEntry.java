package wfederico.pneumacare.timeline.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single, source-agnostic entry in a patient's clinical timeline (PNMC-133).
 *
 * <p>Produced by the per-source ports already typed and timestamped, so the
 * {@code TimelineService} only has to merge and sort. The {@code payload} is the
 * source context's own response DTO (e.g. {@code EvaluationResponse}); it is typed
 * as {@link Object} because the three sources contribute different records.
 *
 * @param type       which source produced the entry
 * @param id         the source row's UUID — used only as a deterministic sort
 *                   tie-breaker when two entries share an {@code occurredAt}; the
 *                   value is also present inside {@code payload}
 * @param occurredAt the clinically-relevant instant the event occurred
 * @param payload    the source context's response DTO for this event
 */
public record TimelineEntry(TimelineEventType type, UUID id, OffsetDateTime occurredAt, Object payload) {
}
