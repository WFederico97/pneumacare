package wfederico.pneumacare.inventory.web.dto;

import jakarta.validation.constraints.NotNull;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;

/** Status-only partial update; registration data is immutable by design. */
public record UpdateVentilatorStatusRequest(
        @NotNull VentilatorStatus status
) {
}
