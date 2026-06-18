package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthenticatedActor}, the static resolver that turns the
 * current {@code SecurityContext} principal into an actor UUID for audit records.
 *
 * <p>Mirrors the behaviour of the existing
 * {@code EvaluationPersistenceService.resolveCreatedBy()}: the JWT {@code sub}
 * claim (exposed as {@code Authentication.getName()}) is a UUID in staging/prod,
 * while dev has no parseable principal and must fall back to the nil UUID.
 */
class AuthenticatedActorTest {

    private static final UUID NIL = new UUID(0L, 0L);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("returns nil UUID when there is no authentication")
    void noAuthentication_returnsNil() {
        SecurityContextHolder.clearContext();

        assertThat(AuthenticatedActor.currentActorId()).isEqualTo(NIL);
    }

    @Test
    @DisplayName("returns nil UUID for an anonymous authentication token")
    void anonymous_returnsNil() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(AuthenticatedActor.currentActorId()).isEqualTo(NIL);
    }

    @Test
    @DisplayName("returns nil UUID when the principal name is not a UUID")
    void nonUuidPrincipal_returnsNil() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-uuid", "n/a",
                        AuthorityUtils.NO_AUTHORITIES));

        assertThat(AuthenticatedActor.currentActorId()).isEqualTo(NIL);
    }

    @Test
    @DisplayName("returns the principal UUID when the name is a valid UUID (JWT sub)")
    void uuidPrincipal_returnsThatUuid() {
        UUID sub = UUID.fromString("11111111-2222-3333-4444-555555555555");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sub.toString(), "n/a",
                        AuthorityUtils.NO_AUTHORITIES));

        assertThat(AuthenticatedActor.currentActorId()).isEqualTo(sub);
    }
}
