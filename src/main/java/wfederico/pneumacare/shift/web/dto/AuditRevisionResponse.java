package wfederico.pneumacare.shift.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One Envers revision of an audited record, returned by the audit query API
 * (PNMC-134).
 *
 * <p>{@code entity} is the record's snapshot at this revision ({@link ShiftResponse} or
 * {@link HandoverResponse}); it is {@code null} for a {@code DELETE} revision.
 *
 * @param <T> the audited record's response type
 */
public record AuditRevisionResponse<T>(

        @Schema(description = "Envers revision number (monotonically increasing).", example = "42")
        long revisionNumber,

        @Schema(description = "Kind of change captured by this revision.",
                example = "UPDATE", allowableValues = {"CREATE", "UPDATE", "DELETE"})
        String revisionType,

        @Schema(description = "UUID of the user who made the change; nil UUID when unauthenticated (dev).",
                example = "eeeeeeee-0000-0000-0000-000000000001")
        UUID actorId,

        @Schema(description = "UTC timestamp of the revision (ISO-8601).",
                example = "2026-06-13T20:00:00Z")
        OffsetDateTime revisionTimestamp,

        @Schema(description = "Record snapshot at this revision; null for a DELETE revision.")
        T entity
) {
}
