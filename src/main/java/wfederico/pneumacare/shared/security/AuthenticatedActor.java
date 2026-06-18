package wfederico.pneumacare.shared.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Resolves the current request's actor UUID from the Spring Security context for
 * use in audit records (e.g. the Envers revision actor).
 *
 * <p>In staging/prod the JWT {@code sub} claim is a UUID string exposed via
 * {@link Authentication#getName()}. In dev there is no JWT, so {@code getName()}
 * returns a non-UUID value (or there is no authentication at all); in that case
 * the {@link #NIL_UUID nil UUID} is returned so persistence is never blocked.
 *
 * <p>This is a static helper rather than a Spring bean because it is consumed by a
 * Hibernate-instantiated {@code RevisionListener}, which cannot receive injected
 * beans. It mirrors {@code EvaluationPersistenceService.resolveCreatedBy()}.
 */
public final class AuthenticatedActor {

    /** Returned when no authenticated UUID principal is available (e.g. dev profile). */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    private AuthenticatedActor() {
    }

    /**
     * @return the authenticated principal's UUID, or {@link #NIL_UUID} when the
     *         caller is anonymous/unauthenticated or the principal name is not a UUID.
     */
    public static UUID currentActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return NIL_UUID;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return NIL_UUID;
        }
    }
}
