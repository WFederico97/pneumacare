package wfederico.pneumacare.config.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for updating one editable system setting's value. */
public record UpdateSettingRequest(
        @NotBlank(message = "El valor es obligatorio")
        @Size(max = 500, message = "El valor no puede superar 500 caracteres")
        String value) {
}
