package wfederico.pneumacare.procedures.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for airway-event endpoints.
 *
 * <p>{@code resultingStatus} is the patient's respiratory status produced by this
 * event. It is <em>derived</em> from {@link AirwayEventType#resultingStatus()} — a
 * pure function of the event type — rather than persisted on the row, so it is
 * correct both for the just-registered event and for every historical event in the
 * GET listing.
 */
public record AirwayEventResponse(

        @Schema(description = "Airway event UUID.", example = "ffffffff-0000-0000-0000-000000000001")
        UUID id,

        @Schema(description = "UUID of the patient.", example = "aaaaaaaa-0000-0000-0000-000000000001")
        UUID patientId,

        @Schema(description = "UUID of the OPEN shift the event was registered under.",
                example = "bbbbbbbb-0000-0000-0000-000000000001")
        UUID shiftId,

        @Schema(description = "Airway event type.", example = "INTUBATION")
        AirwayEventType eventType,

        @Schema(description = "Resulting patient respiratory status after this event.",
                example = "INTUBATED")
        RespiratoryStatus resultingStatus,

        @Schema(description = "Clinically-reported event timestamp (ISO-8601).",
                example = "2026-06-13T09:30:00Z")
        OffsetDateTime eventTimestamp,

        @Schema(description = "UUID of the user who registered the event.",
                example = "eeeeeeee-0000-0000-0000-000000000001")
        UUID createdBy,

        @Schema(description = "UTC timestamp when the row was persisted (ISO-8601).",
                example = "2026-06-13T09:30:05Z")
        OffsetDateTime createdAt
) {
    /** Maps an {@link AirwayEventJpaEntity} to this response DTO. */
    public static AirwayEventResponse from(AirwayEventJpaEntity entity) {
        return new AirwayEventResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getShiftId(),
                entity.getEventType(),
                entity.getEventType().resultingStatus(),
                entity.getEventTime(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
