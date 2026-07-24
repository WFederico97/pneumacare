package wfederico.pneumacare.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.filter.OncePerRequestFilter;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects a structurally valid JWT whose account no longer exists or has been
 * disabled.
 *
 * <p>The self-issued JWT is stateless: the resource server checks signature and
 * expiry only. Without this filter, deleting or disabling a user left their
 * session fully working until the token expired ({@code app.security.jwt.expiration},
 * 8 h by default) — so offboarding a clinician, or revoking a compromised
 * account, had no immediate effect.
 *
 * <p>It also enforces the token generation: {@code token_version} in the token
 * must match the stored column. Bumping that column (on password change) ends
 * every session already issued, including ones on other devices.
 *
 * <p>Cost is one primary-key lookup per authenticated request. It runs after the
 * resource server has authenticated the token, so unauthenticated and permitted
 * requests never touch the database.
 *
 * <p>Deliberately not a {@code @Component}: it is wired only into the
 * staging/prod filter chain, so web-layer test slices — which have no JPA
 * repositories — never try to construct it.
 */
@Slf4j
public class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public ActiveAccountFilter(UserRepository userRepository,
                               AuthenticationEntryPoint authenticationEntryPoint) {
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {

            if (!isAccountActive(jwt)) {
                SecurityContextHolder.clearContext();
                authenticationEntryPoint.commence(request, response,
                        new BadCredentialsException("Cuenta deshabilitada o inexistente"));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** {@code true} when the token's subject maps to an existing, enabled user. */
    private boolean isAccountActive(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return false;
        }
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejecting token: sub claim is not a UUID");
            return false;
        }
        UserJpaEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            // Log the id only — never the display name or username.
            log.warn("Rejecting token for missing or disabled account: userId={}", userId);
            return false;
        }

        Object claim = jwt.getClaim("token_version");
        // A token minted before token versioning existed carries no claim; treat it
        // as stale rather than trusting it.
        int tokenVersion = claim instanceof Number n ? n.intValue() : -1;
        if (tokenVersion != user.getTokenVersion()) {
            log.warn("Rejecting superseded token: userId={}, tokenVersion={}, expected={}",
                    userId, tokenVersion, user.getTokenVersion());
            return false;
        }
        return true;
    }
}
