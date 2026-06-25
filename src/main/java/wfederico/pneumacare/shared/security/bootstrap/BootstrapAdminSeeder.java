package wfederico.pneumacare.shared.security.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.shared.security.user.Role;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;

import java.util.EnumSet;

/**
 * Seeds exactly one enabled {@code ROLE_ADMIN} user on startup when none exists,
 * using a BCrypt hash derived from a configuration-provided initial password.
 *
 * <p>Idempotent: a no-op once the admin exists. Active only when
 * {@code app.security.bootstrap-admin.enabled=true} (staging/prod) so it never
 * runs in dev or during integration tests. Fails fast if the initial password is
 * missing, rather than seeding an unusable account.
 */
@Component
@ConditionalOnProperty(prefix = "app.security.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminSeeder(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                BootstrapAdminProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(properties.getUsername()).isPresent()) {
            log.info("Bootstrap admin '{}' already present; skipping seed.", properties.getUsername());
            return;
        }

        String initialPassword = properties.getInitialPassword();
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap admin is enabled but no initial password is set. "
                            + "Provide PNMC_BOOTSTRAP_ADMIN_PASSWORD "
                            + "(app.security.bootstrap-admin.initial-password).");
        }

        UserJpaEntity admin = UserJpaEntity.builder()
                .username(properties.getUsername())
                .displayName(properties.getDisplayName())
                .passwordHash(passwordEncoder.encode(initialPassword))
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_ADMIN))
                .build();

        userRepository.save(admin);
        log.info("Seeded bootstrap admin user '{}'.", properties.getUsername());
    }
}
