package wfederico.pneumacare.shared.security;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * Canonical role hierarchy. ADMIN inherits everything; COMPLIANCE is an
 * orthogonal read/audit role that inherits nothing.
 */
public final class AppRoleHierarchy {

    private AppRoleHierarchy() {
    }

    public static RoleHierarchy create() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("CHIEF_OF_GUARD", "COMPLIANCE", "DIRECTOR")
                .role("CHIEF_OF_GUARD").implies("THERAPIST")
                .build();
    }
}
