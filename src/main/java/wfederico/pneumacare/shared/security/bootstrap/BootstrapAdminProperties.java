package wfederico.pneumacare.shared.security.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the bootstrap admin settings from {@code app.security.bootstrap-admin.*}.
 *
 * <p>{@code initialPassword} must be supplied via the
 * {@code PNMC_BOOTSTRAP_ADMIN_PASSWORD} environment variable in staging/prod.
 * <strong>Never hardcode it in source or migrations.</strong> The seeded
 * password is documented as rotate-on-first-login.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
public class BootstrapAdminProperties {

    /** When false (default), the seeder does not run. */
    private boolean enabled = false;

    private String username = "admin";

    private String displayName = "System Administrator";

    /** Plaintext initial password, BCrypt-hashed at seed time. Sourced from env. */
    private String initialPassword;
}
