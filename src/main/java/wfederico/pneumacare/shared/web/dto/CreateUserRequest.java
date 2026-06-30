package wfederico.pneumacare.shared.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import wfederico.pneumacare.shared.security.user.Role;

import java.util.Set;

/**
 * Admin payload to create a user with an explicit role set. Granting
 * {@code ROLE_ADMIN} is restricted to admin callers (enforced in the service).
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 150) String displayName,
        @NotEmpty Set<Role> roles,
        Boolean enabled) {
}
