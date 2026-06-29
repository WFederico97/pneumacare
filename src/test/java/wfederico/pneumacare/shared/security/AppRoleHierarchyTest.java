package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppRoleHierarchyTest {

    private List<String> reachable(String role) {
        return AppRoleHierarchy.create()
                .getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority(role)))
                .stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void admin_reachesAllRoles() {
        assertThat(reachable("ROLE_ADMIN"))
                .contains("ROLE_ADMIN", "ROLE_CHIEF_OF_GUARD", "ROLE_THERAPIST", "ROLE_COMPLIANCE");
    }

    @Test
    void chief_reachesTherapistButNotComplianceOrAdmin() {
        assertThat(reachable("ROLE_CHIEF_OF_GUARD"))
                .contains("ROLE_CHIEF_OF_GUARD", "ROLE_THERAPIST")
                .doesNotContain("ROLE_ADMIN", "ROLE_COMPLIANCE");
    }

    @Test
    void therapist_reachesOnlyItself() {
        assertThat(reachable("ROLE_THERAPIST")).containsExactly("ROLE_THERAPIST");
    }

    @Test
    void compliance_reachesOnlyItself() {
        assertThat(reachable("ROLE_COMPLIANCE")).containsExactly("ROLE_COMPLIANCE");
    }
}
