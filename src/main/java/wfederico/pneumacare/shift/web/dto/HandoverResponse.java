package wfederico.pneumacare.shift.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for shift handover endpoints. Represents an immutable handover note.
 *
 * <p>{@code createdAt} is the persistence timestamp (the ticket's {@code created_at}),
 * taken from the audited column.
 */
public record HandoverResponse(

        @Schema(description = "Handover note UUID.", example = "ffffffff-0000-0000-0000-000000000001")
        UUID id,

        @Schema(description = "UUID of the shift the note belongs to.",
                example = "bbbbbbbb-0000-0000-0000-000000000001")
        UUID shiftId,

        @Schema(description = "UUID of the author (derived from the authenticated principal).",
                example = "eeeeeeee-0000-0000-0000-000000000001")
        UUID authorId,

        @Schema(description = "Note content.",
                example = "Cama 3 estable, destete en curso.")
        String notesContent,

        @Schema(description = "UTC timestamp when the note was created (ISO-8601).",
                example = "2026-06-13T19:45:00Z")
        OffsetDateTime createdAt
) {
    /** Maps a {@link ShiftHandoverJpaEntity} to this response DTO. */
    public static HandoverResponse from(ShiftHandoverJpaEntity entity) {
        return new HandoverResponse(
                entity.getId(),
                entity.getShiftId(),
                entity.getAuthorId(),
                entity.getNotesContent(),
                entity.getCreatedAt());
    }
}
