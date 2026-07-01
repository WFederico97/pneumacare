package wfederico.pneumacare.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wfederico.pneumacare.notification.application.AlertAuditPort;
import wfederico.pneumacare.notification.application.AlertNotification;
import wfederico.pneumacare.notification.application.PatientRiskAlertListener;
import wfederico.pneumacare.notification.application.WebhookNotificationPort;
import wfederico.pneumacare.shared.event.PatientRiskEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PatientRiskAlertListenerTest {

    private static final UUID EVENT   = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID PATIENT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SHIFT   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final Instant FIXED = Instant.parse("2026-06-30T18:45:00Z");

    @Mock
    private WebhookNotificationPort webhookNotificationPort;

    @Mock
    private AlertAuditPort alertAuditPort;

    private final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    private PatientRiskAlertListener listener;

    private static PatientRiskEvent event() {
        return new PatientRiskEvent(EVENT, PATIENT, SHIFT, "Cama 3",
                List.of(new PatientRiskEvent.BreachedMetric("RSBI", 110.0)));
    }

    @BeforeEach
    void setUp() {
        listener = new PatientRiskAlertListener(webhookNotificationPort, alertAuditPort, clock);
    }

    @Test
    void onPatientRiskEvent_sendsMappedNotificationWithClockTimestamp() {
        listener.onPatientRiskEvent(event());

        ArgumentCaptor<AlertNotification> captor = ArgumentCaptor.forClass(AlertNotification.class);
        verify(webhookNotificationPort).send(captor.capture());

        AlertNotification sent = captor.getValue();
        assertThat(sent.patientId()).isEqualTo(PATIENT);
        assertThat(sent.shiftId()).isEqualTo(SHIFT);
        assertThat(sent.bedLabel()).isEqualTo("Cama 3");
        assertThat(sent.timestamp()).isEqualTo(FIXED);
        assertThat(sent.breachedMetrics())
                .containsExactly(new AlertNotification.Metric("RSBI", 110.0));
    }

    @Test
    void onPatientRiskEvent_success_recordsPendingThenSendsThenMarksDelivered() {
        listener.onPatientRiskEvent(event());

        InOrder inOrder = inOrder(alertAuditPort, webhookNotificationPort);
        inOrder.verify(alertAuditPort).recordPending(eq(EVENT), any());
        inOrder.verify(webhookNotificationPort).send(any());
        inOrder.verify(alertAuditPort).markDelivered(EVENT);
        verify(alertAuditPort, never()).markFailed(any());
    }

    @Test
    void onPatientRiskEvent_sendThrows_marksFailedAndSwallows() {
        doThrow(new RuntimeException("connection refused"))
                .when(webhookNotificationPort).send(any());

        assertThatCode(() -> listener.onPatientRiskEvent(event())).doesNotThrowAnyException();

        verify(alertAuditPort).markFailed(EVENT);
        verify(alertAuditPort, never()).markDelivered(any());
    }

    @Test
    void onPatientRiskEvent_recordPendingThrows_dispatchStillProceeds() {
        doThrow(new RuntimeException("db down"))
                .when(alertAuditPort).recordPending(eq(EVENT), any());

        assertThatCode(() -> listener.onPatientRiskEvent(event())).doesNotThrowAnyException();

        verify(webhookNotificationPort).send(any());
        verify(alertAuditPort).markDelivered(EVENT);
    }
}
