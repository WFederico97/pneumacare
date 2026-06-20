package wfederico.pneumacare.procedures.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import wfederico.pneumacare.procedures.domain.AirwayEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for registering an airway event: {@code POST /api/v1/procedures/airway}.
 *
 * <p>Only these three fields are accepted from the client. {@code shiftId} (derived
 * from the patient's OPEN shift), {@code createdBy} (the current user) and the
 * resulting respiratory status are all set server-side and are intentionally not
 * part of this contract.
 *
 * <p>JSON uses camelCase (the project default): {@code patientId}, {@code eventType},
 * {@code eventTimestamp}.
 */
public record CreateAirwayEventRequest(

        @Schema(description = "UUID of the patient the event applies to.",
                example = "aaaaaaaa-0000-0000-0000-000000000001")
        @NotNull(message = "El id del paciente es obligatorio")
        UUID patientId,

        @Schema(description = "Airway event type.", example = "INTUBATION")
        @NotNull(message = "El tipo de evento es obligatorio")
        AirwayEventType eventType,

        @Schema(description = "Clinically-reported event timestamp (ISO-8601, UTC).",
                example = "2026-06-13T09:30:00Z")
        @NotNull(message = "La marca de tiempo del evento es obligatoria")
        OffsetDateTime eventTimestamp
) {}
