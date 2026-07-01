package wfederico.pneumacare.shared.security.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import wfederico.pneumacare.shared.security.user.Role;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminSeederTest {

    @Mock
    UserRepository userRepository;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    BootstrapAdminProperties properties = new BootstrapAdminProperties();

    BootstrapAdminSeeder seeder() {
        return new BootstrapAdminSeeder(userRepository, passwordEncoder, properties);
    }

    @Test
    void seedsAnEnabledAdminWithBcryptHashWhenAbsent() throws Exception {
        properties.setUsername("admin");
        properties.setDisplayName("System Administrator");
        properties.setInitialPassword("s3cret-initial");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        seeder().run(null);

        ArgumentCaptor<UserJpaEntity> saved = ArgumentCaptor.forClass(UserJpaEntity.class);
        verify(userRepository).save(saved.capture());
        UserJpaEntity admin = saved.getValue();

        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getRoles()).containsExactly(Role.ROLE_ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo("s3cret-initial");
        assertThat(passwordEncoder.matches("s3cret-initial", admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() throws Exception {
        properties.setUsername("admin");
        properties.setInitialPassword("s3cret-initial");
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new UserJpaEntity()));

        seeder().run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void failsFastWhenInitialPasswordIsBlank() {
        properties.setUsername("admin");
        properties.setInitialPassword("   ");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seeder().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PNMC_BOOTSTRAP_ADMIN_PASSWORD");

        verify(userRepository, never()).save(any());
    }
}
