package wfederico.pneumacare.shared.security;

import java.util.UUID;

/**
 * Outbound port: resolves the UUID of the user performing the current request.
 *
 * <p>This is a cross-cutting security/request-context concern, not owned by any
 * single bounded context, so it lives in the shared kernel and is reused by every
 * context that needs the caller's identity (shift, procedures, …).
 *
 * <p>Authentication is not yet implemented (separate backlog USs, next sprint).
 * Until then the adapter returns a configured default. When auth lands, only the
 * adapter changes — this port and its callers stay the same.
 */
public interface CurrentUserPort {
    UUID currentUserId();
}
