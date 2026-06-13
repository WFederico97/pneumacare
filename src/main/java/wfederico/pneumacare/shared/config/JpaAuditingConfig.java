package wfederico.pneumacare.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Enables JPA auditing for {@code @CreatedDate}/{@code @LastModifiedDate} on
 * {@link wfederico.pneumacare.shared.data.EntityBase}.
 *
 * <p>The audit fields are {@link OffsetDateTime} (to match the {@code TIMESTAMPTZ}
 * columns). Spring Data's default {@code DateTimeProvider} returns {@code LocalDateTime},
 * which it cannot convert to {@code OffsetDateTime}. This custom provider returns the
 * current instant as a UTC {@code OffsetDateTime}, so no conversion is needed.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}