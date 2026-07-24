package wfederico.pneumacare.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import wfederico.pneumacare.inventory.domain.VentilatorBrand;


/**
 * Registration payload for a physical ventilator. An unknown {@code brand}
 * value fails Jackson enum binding and surfaces as a 400 before validation.
 */
public record CreateVentilatorRequest(
        @NotBlank @Size(max = 100) String serialNumber,
        @NotNull VentilatorBrand brand,
        @NotBlank @Size(max = 100) String modelName
) {
}
