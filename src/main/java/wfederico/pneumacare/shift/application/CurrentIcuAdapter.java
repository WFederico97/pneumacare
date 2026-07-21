package wfederico.pneumacare.shift.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.UUID;

/**
 * Resolves the ICU for the current request from the authenticated principal's
 * {@code icu_id} JWT claim — the same claim the bed grid uses — so shift
 * operations are scoped to the session's ICU rather than a fixed configured one.
 *
 * <p>In the {@code dev} profile no JWT is present, so it falls back to the
 * configured {@code app.security.dev-default-icu-id} (the dev-seeded ICU).
 */
@Component
public class CurrentIcuAdapter implements CurrentIcuPort {

    private final Environment environment;

    @Value("${app.security.dev-default-icu-id:cccccccc-0000-0000-0000-000000000001}")
    private String devDefaultIcuId;

    public CurrentIcuAdapter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public UUID currentIcuId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            if (environment.matchesProfiles("dev")) {
                return UUID.fromString(devDefaultIcuId);
            }
            throw new BusinessLayerException("No autenticado", HttpStatus.UNAUTHORIZED);
        }

        Object icuIdClaim = jwt.getClaim("icu_id");
        if (icuIdClaim == null) {
            throw new BusinessLayerException("Token inválido: falta claim icu_id", HttpStatus.BAD_REQUEST);
        }
        try {
            return UUID.fromString(icuIdClaim.toString());
        } catch (IllegalArgumentException ex) {
            throw new BusinessLayerException("Token inválido: claim icu_id no es UUID", HttpStatus.BAD_REQUEST);
        }
    }
}
