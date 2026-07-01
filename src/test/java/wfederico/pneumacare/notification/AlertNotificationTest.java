package wfederico.pneumacare.notification;

import org.junit.jupiter.api.Test;
import wfederico.pneumacare.notification.application.AlertNotification;
import wfederico.pneumacare.shared.event.PatientRiskEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertNotificationTest {

    private static final UUID EVENT   = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID PATIENT = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SHIFT   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final Instant TS   = Instant.parse("2026-06-30T18:45:00Z");

    @Test
    void from_mapsAllFieldsIncludingMetricsAndTimestamp() {
        PatientRiskEvent event = new PatientRiskEvent(EVENT, PATIENT, SHIFT, "Cama 3",
                List.of(new PatientRiskEvent.BreachedMetric("RSBI", 110.0),
                        new PatientRiskEvent.BreachedMetric("PAFI", 100.0)));

        AlertNotification n = AlertNotification.from(event, TS);

        assertThat(n.patientId()).isEqualTo(PATIENT);
        assertThat(n.shiftId()).isEqualTo(SHIFT);
        assertThat(n.bedLabel()).isEqualTo("Cama 3");
        assertThat(n.timestamp()).isEqualTo(TS);
        assertThat(n.breachedMetrics()).containsExactly(
                new AlertNotification.Metric("RSBI", 110.0),
                new AlertNotification.Metric("PAFI", 100.0));
    }

    @Test
    void from_nullBedLabel_preserved() {
        PatientRiskEvent event = new PatientRiskEvent(EVENT, PATIENT, SHIFT, null,
                List.of(new PatientRiskEvent.BreachedMetric("CSTAT", 25.0)));

        assertThat(AlertNotification.from(event, TS).bedLabel()).isNull();
    }
}
