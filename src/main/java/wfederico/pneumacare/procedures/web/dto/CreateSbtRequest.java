package wfederico.pneumacare.procedures.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import wfederico.pneumacare.procedures.domain.ToleranceResult;

import java.util.UUID;

/**
 * Request body for recording an SBT: {@code POST /api/v1/procedures/sbt}.
 *
 * <p>Only these three fields are accepted from the client. {@code shiftId} (the
 * patient's OPEN shift), {@code performedBy} (the current user) and the recorded
 * timestamp are all set server-side and are intentionally not part of this contract.
 *
 * <p>The positive-duration rule ({@code durationMinutes > 0}) is enforced in the
 * service (returns {@code 422}) rather than via {@code @Positive}, so a well-formed
 * request with an invalid value is {@code 422} rather than {@code 400}.
 *
 * <p>JSON uses camelCase (the project default): {@code patientId},
 * {@code durationMinutes}, {@code toleranceResult}.
 */
public record CreateSbtRequest(

        @Schema(description = "UUID of the patient the trial was performed on.",
                example = "aaaaaaaa-0000-0000-0000-000000000001")
        @NotNull(message = "El id del paciente es obligatorio")
        UUID patientId,

        @Schema(description = "Trial duration in minutes (positive integer).", example = "30")
        @NotNull(message = "La duración del SBT es obligatoria")
        Integer durationMinutes,

        @Schema(description = "Trial outcome.", example = "SUCCESS")
        @NotNull(message = "El resultado de tolerancia es obligatorio")
        ToleranceResult toleranceResult
) {}
