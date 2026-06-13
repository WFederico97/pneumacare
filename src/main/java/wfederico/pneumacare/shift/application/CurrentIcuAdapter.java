package wfederico.pneumacare.shift.application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary {@link CurrentIcuPort} adapter used until authentication is implemented.
 *
 * <p>Returns the configured {@code app.security.dev-default-icu-id} (the seeded ICU).
 * TODO (auth US): resolve the ICU from the authenticated principal's {@code icu_id} claim.
 */
@Component
public class CurrentIcuAdapter implements CurrentIcuPort{
    @Value("${app.security.dev-default-icu-id:cccccccc-0000-0000-0000-000000000001}")
    private String defaulIcuId;

    @Override
    public UUID currentIcuId(){
        return UUID.fromString(defaulIcuId);
    }

}
