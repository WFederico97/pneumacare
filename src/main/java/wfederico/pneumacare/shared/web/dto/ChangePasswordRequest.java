package wfederico.pneumacare.shared.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service password change for the signed-in user.
 *
 * <p>The current password is required so that a briefly unattended session
 * cannot be used to lock the real owner out.
 */
@Schema(description = "Password change for the authenticated user.")
public record ChangePasswordRequest(

        @Schema(description = "The account's current password.")
        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        @Schema(description = "New password, at least 8 characters.")
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String newPassword) {
}
