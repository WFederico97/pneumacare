package wfederico.pneumacare.inventory.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Release a ventilator's active assignment. */
public record UnassignAssetRequest(
        @NotNull UUID ventilatorId
) {
}
