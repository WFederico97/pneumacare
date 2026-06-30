package wfederico.pneumacare.shared.web.dto;

import java.util.List;
import java.util.UUID;

/** Admin view of a user. Never carries the password hash. */
public record UserResponse(
        UUID id,
        String username,
        String displayName,
        List<String> roles,
        boolean enabled) {
}
