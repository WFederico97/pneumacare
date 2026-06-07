package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIcuBedRequest(
        @Schema(
                description = "Bed number visible in the dashboard.",
                example = "BED-004")
        @NotBlank(message = "El número de cama es obligatorio")
        @Size(max = 50, message = "El número de cama no debe superar 50 caracteres")
        String bedNumber) {
}
