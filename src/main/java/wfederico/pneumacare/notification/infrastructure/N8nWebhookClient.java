package wfederico.pneumacare.notification.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import wfederico.pneumacare.notification.application.AlertNotification;
import wfederico.pneumacare.notification.application.WebhookNotificationPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                .body(toRequestBody(notification));

        if (StringUtils.hasText(properties.getSecret())) {
            request = request.header("X-Webhook-Secret", properties.getSecret());
        }

        request.retrieve().toBodilessEntity();
    }

    /** Maps the payload to a snake_case structure matching the PNMC-101 contract. */
    static Map<String, Object> toRequestBody(AlertNotification n) {
        List<Map<String, Object>> metrics = n.breachedMetrics().stream()
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("metric_name", m.metricName());
                    entry.put("value", m.value());
                    return entry;
                })
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", n.patientId());
        body.put("shift_id", n.shiftId());
        body.put("bed_label", n.bedLabel());   // LinkedHashMap allows null
        body.put("breached_metrics", metrics);
        body.put("timestamp", n.timestamp().toString());
        return body;
    }
}
