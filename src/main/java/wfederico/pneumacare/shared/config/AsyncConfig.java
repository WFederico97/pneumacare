package wfederico.pneumacare.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables Spring's {@code @Async} support application-wide. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
