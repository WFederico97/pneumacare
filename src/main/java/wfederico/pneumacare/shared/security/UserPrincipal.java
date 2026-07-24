package wfederico.pneumacare.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security {@link UserDetails} backed by a {@link UserJpaEntity}.
 *
 * <p>Carries the user UUID so the JWT {@code sub} claim and the authenticated
 * principal name resolve to the actor id used for audit attribution.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final boolean enabled;
    private final int tokenVersion;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String username, String passwordHash, String displayName,
                         boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, passwordHash, displayName, enabled, 0, authorities);
    }

    public UserPrincipal(UUID id, String username, String passwordHash, String displayName,
                         boolean enabled, int tokenVersion,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = enabled;
        this.tokenVersion = tokenVersion;
        this.authorities = authorities;
    }

    /** Session generation; embedded in the token and re-checked on every request. */
    public int getTokenVersion() {
        return tokenVersion;
    }

    public static UserPrincipal from(UserJpaEntity user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) role.authority())
                .toList();
        return new UserPrincipal(
                user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getDisplayName(), user.isEnabled(), user.getTokenVersion(), authorities);
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
