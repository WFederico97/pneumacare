package wfederico.pneumacare.procedures.application;

import java.util.UUID;

/**
 * Outbound port: resolves the UUID of the user performing the current request.
 *
 * <p>Authentication is not yet implemented (separate backlog USs, next sprint).
 * Until then the adapter returns a configured default. When auth lands, only the
 * adapter changes — this port and its callers stay the same.
 */
public interface CurrentUserPort {
    UUID currentUserId();
}
