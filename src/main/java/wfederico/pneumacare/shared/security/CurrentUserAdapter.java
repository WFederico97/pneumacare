package wfederico.pneumacare.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the UUID of the user performing the current request.
 *
 * <p>Returns the authenticated principal's UUID (the JWT {@code sub}) when present;
 * in dev — where there is no authentication — falls back to a configured default
 * so the {@code *_user_id} columns are still populated.
 */
@Component
public class CurrentUserAdapter implements CurrentUserPort {
    @Value("${app.security.dev-default-chief-user-id:eeeeeeee-0000-0000-0000-000000000001}")
    private String defaultUserId;

    @Override
    public UUID currentUserId() {
        UUID actor = AuthenticatedActor.currentActorId();
        if (!AuthenticatedActor.NIL_UUID.equals(actor)) {
            return actor;
        }
        return UUID.fromString(defaultUserId);
    }
}
