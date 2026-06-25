package wfederico.pneumacare.shared.security.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void authorityValueEqualsEnumName() {
        for (Role role : Role.values()) {
            GrantedAuthority authority = role.authority();
            assertThat(authority.getAuthority()).isEqualTo(role.name());
            assertThat(authority.getAuthority()).startsWith("ROLE_");
        }
    }

    @Test
    void declaresTheFourCanonicalRoles() {
        assertThat(Role.values())
                .containsExactlyInAnyOrder(
                        Role.ROLE_ADMIN,
                        Role.ROLE_CHIEF_OF_GUARD,
                        Role.ROLE_THERAPIST,
                        Role.ROLE_COMPLIANCE);
    }
}
