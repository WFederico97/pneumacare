package wfederico.pneumacare.shared.security.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Canonical set of application roles and the single source of role strings.
 *
 * <p>Each constant's name is also its Spring Security authority value (the
 * {@code ROLE_} prefix is part of the name), so {@link #authority()} never
 * derives or rewrites the string. The {@code user_roles.role} column is
 * constrained by a database CHECK to exactly these names.
 *
 * <p>COMPLIANCE corresponds to the legacy {@code SCOPE_audit} access.
 */
public enum Role {

    ROLE_ADMIN,
    ROLE_CHIEF_OF_GUARD,
    ROLE_THERAPIST,
    ROLE_COMPLIANCE;

    /** @return this role as a Spring Security authority (value == enum name). */
    public GrantedAuthority authority() {
        return new SimpleGrantedAuthority(name());
    }
}
