package wfederico.pneumacare.notification.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import wfederico.pneumacare.notification.application.AlertNotification;
import wfederico.pneumacare.notification.application.WebhookNotificationPort;

/**
 * RestClient adapter that POSTs the alert as snake_case JSON to the n8n webhook.
 * A blank URL disables dispatch (dev). On transport failure the RestClient throws,
 * which the listener catches.
 */
@Slf4j
@Component
public class N8nWebhookClient implements WebhookNotificationPort {

    private final RestClient restClient;
    private final WebhookProperties properties;

    public N8nWebhookClient(RestClient notificationRestClient, WebhookProperties properties) {
        this.restClient = notificationRestClient;
        this.properties = properties;
    }

    @Override
    public void send(AlertNotification notification) {
        if (!StringUtils.hasText(properties.getUrl())) {
            log.debug("Notification webhook disabled (blank URL) — skipping dispatch.");
            return;
        }

        RestClient.RequestBodySpec request = restClient.post()
                .uri(properties.getUrl())
                .body(notification.toPayloadMap());

        if (StringUtils.hasText(properties.getSecret())) {
            request = request.header("X-Webhook-Secret", properties.getSecret());
        }

        request.retrieve().toBodilessEntity();
    }
}
