package wfederico.pneumacare.shared.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import wfederico.pneumacare.shared.security.user.Role;

/**
 * Self-registration payload. The chosen {@code role} is restricted at the
 * controller to the clinical roles a user may self-assign
 * ({@code ROLE_THERAPIST} or {@code ROLE_CHIEF_OF_GUARD}); privileged roles
 * ({@code ROLE_ADMIN}, {@code ROLE_COMPLIANCE}) are admin-provisioned only.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 150) String displayName,
        @NotNull Role role) {
}
