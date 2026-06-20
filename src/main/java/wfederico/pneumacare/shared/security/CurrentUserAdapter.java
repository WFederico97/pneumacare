package wfederico.pneumacare.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary {@link CurrentUserPort} adapter used until authentication is implemented.
 *
 * <p>Returns a configured default user UUID. In dev no FK is enforced on the
 * {@code *_user_id} columns that store it, so this value need not reference a real
 * row. TODO (auth US, next sprint): replace the body with JWT-principal resolution.
 */
@Component
public class CurrentUserAdapter implements CurrentUserPort {
    @Value("${app.security.dev-default-chief-user-id:eeeeeeee-0000-0000-0000-000000000001}")
    private String defaultUserId;

    @Override
    public UUID currentUserId() {
        return UUID.fromString(defaultUserId);
    }
}
