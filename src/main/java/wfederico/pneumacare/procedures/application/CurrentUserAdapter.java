package wfederico.pneumacare.procedures.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserAdapter implements CurrentUserPort{
    @Value("${app.security.dev-default-chief-user-id:eeeeeeee-0000-0000-0000-000000000001}")
    private String defaultUserId;

    @Override
    public UUID currentUserId() {
        return UUID.fromString(defaultUserId);
    }
}
