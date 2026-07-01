package wfederico.pneumacare.notification.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Binds {@code app.notifications.webhook.*}. Blank {@code url} disables dispatch (dev). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.notifications.webhook")
public class WebhookProperties {

    private String url = "";
    private String secret = "";
    private Duration connectTimeout = Duration.ofMillis(3000);
    private Duration readTimeout = Duration.ofMillis(3000);
}
