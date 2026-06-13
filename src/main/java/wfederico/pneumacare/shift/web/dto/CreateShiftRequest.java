package wfederico.pneumacare.shift.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for opening a medical shift: {@code POST /api/v1/shifts}.
 *
 * <p>Only {@code icuId} is accepted from the client. {@code startedBy} (the chief),
 * {@code status}, and {@code startedAt} are all set server-side and are intentionally
 * not part of this contract.
 */
public record CreateShiftRequest(
        @Schema(
            description = "UUID of the ICU for wich to open shift",
            example = "cccccccc-0000-0000-0000-000000000001"
        )
        @NotNull(message = "El id de la UCI es obligatorio")
        UUID icuId){}
