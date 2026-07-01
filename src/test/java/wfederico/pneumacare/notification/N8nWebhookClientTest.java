package wfederico.pneumacare.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import wfederico.pneumacare.notification.application.AlertNotification;
import wfederico.pneumacare.notification.infrastructure.N8nWebhookClient;
import wfederico.pneumacare.notification.infrastructure.WebhookProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class N8nWebhookClientTest {

    private static final AlertNotification NOTIFICATION = new AlertNotification(
            UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"),
            "Cama 3",
            List.of(new AlertNotification.Metric("RSBI", 110.0)),
            Instant.parse("2026-06-30T18:45:00Z"));

    private static WebhookProperties props(String url, String secret) {
        WebhookProperties p = new WebhookProperties();
        p.setUrl(url);
        p.setSecret(secret);
        return p;
    }

    @Test
    void send_postsSnakeCaseJsonWithSecretHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nWebhookClient client = new N8nWebhookClient(builder.build(),
                props("https://n8n.example/webhook/abc", "s3cret"));

        server.expect(requestTo("https://n8n.example/webhook/abc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Webhook-Secret", "s3cret"))
                .andExpect(jsonPath("$.patient_id").value("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))
                .andExpect(jsonPath("$.shift_id").value("bbbbbbbb-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.bed_label").value("Cama 3"))
                .andExpect(jsonPath("$.breached_metrics[0].metric_name").value("RSBI"))
                .andExpect(jsonPath("$.timestamp").value("2026-06-30T18:45:00Z"))
                .andRespond(withSuccess());

        client.send(NOTIFICATION);

        server.verify();
    }

    @Test
    void send_noSecret_omitsHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nWebhookClient client = new N8nWebhookClient(builder.build(),
                props("https://n8n.example/webhook/abc", ""));

        server.expect(requestTo("https://n8n.example/webhook/abc"))
                .andExpect(headerDoesNotExist("X-Webhook-Secret"))
                .andRespond(withSuccess());

        client.send(NOTIFICATION);

        server.verify();
    }

    @Test
    void send_blankUrl_makesNoRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        N8nWebhookClient client = new N8nWebhookClient(builder.build(), props("", ""));

        assertThatCode(() -> client.send(NOTIFICATION)).doesNotThrowAnyException();
        server.verify();
    }
}
