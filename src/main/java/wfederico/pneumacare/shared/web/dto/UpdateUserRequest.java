package wfederico.pneumacare.shared.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import wfederico.pneumacare.shared.security.user.Role;

import java.util.Set;

/**
 * Admin payload to update a user's profile, roles and enabled state.
 *
 * <p>{@code password} is optional: when {@code null}/blank the current password
 * is kept; when present it must be at least 8 characters and replaces the hash.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 150) String displayName,
        @NotEmpty Set<Role> roles,
        boolean enabled,
        @Size(min = 8, max = 100) String password) {
}
