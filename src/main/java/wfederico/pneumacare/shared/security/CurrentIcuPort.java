package wfederico.pneumacare.shared.security;

import java.util.UUID;

/**
 * Resolves the ICU UUID of the current request's session, from the {@code icu_id}
 * JWT claim.
 *
 * <p>Session scope, not shift scope: every context that acts "within the caller's
 * ICU" resolves it here rather than accepting an {@code icuId} from the client.
 * Trusting a client-supplied ICU is what broke patient admission and misfiled
 * ventilator registrations.
 */
public interface CurrentIcuPort {
    UUID currentIcuId();
}
