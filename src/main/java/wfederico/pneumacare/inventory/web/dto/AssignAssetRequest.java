package wfederico.pneumacare.inventory.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Assign a ventilator to a patient. */
public record AssignAssetRequest(
        @NotNull UUID ventilatorId,
        @NotNull UUID patientId
) {
}
