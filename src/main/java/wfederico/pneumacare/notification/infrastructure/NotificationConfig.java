package wfederico.pneumacare.notification.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Notification-context wiring: the timeout-configured RestClient, the dedicated
 * async executor for alert dispatch, a UTC clock, and binding of
 * {@link WebhookProperties}.
 */
@Configuration
@EnableConfigurationProperties(WebhookProperties.class)
public class NotificationConfig {

    @Bean
    public RestClient notificationRestClient(WebhookProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean("notificationExecutor")
    public TaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
