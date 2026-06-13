package wfederico.pneumacare.shift.application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.shift.application.CurrentUserPort;

import java.util.UUID;

/**
 * Temporary {@link CurrentUserPort} adapter used until authentication is implemented.
 *
 * <p>Returns a configured default user UUID. In dev no FK is enforced on
 * {@code medical_shifts.chief_user_id}, so this value need not reference a real
 * row. TODO (auth US, next sprint): replace the body with JWT-principal resolution.
 */
@Component
public class CurrentUserAdapter implements CurrentUserPort {
    @Value("${app.security.dev-default-chief-user-id:eeeeeeee-0000-0000-0000-000000000001}")
    private String defaultUserId;

    @Override
    public UUID currentUserId(){
        return UUID.fromString(defaultUserId);
    }
}
