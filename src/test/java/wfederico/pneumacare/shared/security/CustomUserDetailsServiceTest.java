package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import wfederico.pneumacare.shared.security.user.Role;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(repository);

    @Test
    void loadUserByUsername_existingUser_mapsToPrincipalWithRoleAuthorities() {
        UUID id = UUID.randomUUID();
        UserJpaEntity entity = UserJpaEntity.builder()
                .id(id)
                .username("jdoe")
                .passwordHash("$2a$10$hash")
                .displayName("J. Doe")
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_THERAPIST))
                .build();
        when(repository.findByUsername("jdoe")).thenReturn(Optional.of(entity));

        UserDetails details = service.loadUserByUsername("jdoe");

        assertThat(details).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) details;
        assertThat(principal.getId()).isEqualTo(id);
        assertThat(principal.getUsername()).isEqualTo("jdoe");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(principal.getDisplayName()).isEqualTo("J. Doe");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_THERAPIST");
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFound() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
