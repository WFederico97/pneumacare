package wfederico.pneumacare.shared.demo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds demo-seed settings from {@code app.demo.seed.*}.
 *
 * <p>When {@code enabled=false} (default) the {@link DemoDataSeeder} does not run,
 * so demo data never appears in dev/test/CI. Enabled only in {@code .env.prod}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.demo.seed")
public class DemoDataProperties {

    /** When false (default), the demo seeder does not run. */
    private boolean enabled = false;
}
