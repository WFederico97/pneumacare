package wfederico.pneumacare.timeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.timeline.application.AirwayTimelinePort;
import wfederico.pneumacare.timeline.application.EvaluationTimelinePort;
import wfederico.pneumacare.timeline.application.PatientExistencePort;
import wfederico.pneumacare.timeline.application.SbtTimelinePort;
import wfederico.pneumacare.timeline.application.TimelineService;
import wfederico.pneumacare.timeline.domain.TimelineEntry;
import wfederico.pneumacare.timeline.domain.TimelineEventType;
import wfederico.pneumacare.timeline.web.dto.TimelineEntryResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 6, 13, 8, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private PatientExistencePort patientExistencePort;
    @Mock
    private EvaluationTimelinePort evaluationTimelinePort;
    @Mock
    private AirwayTimelinePort airwayTimelinePort;
    @Mock
    private SbtTimelinePort sbtTimelinePort;

    @InjectMocks
    private TimelineService service;

    private static TimelineEntry entry(TimelineEventType type, OffsetDateTime occurredAt) {
        return new TimelineEntry(type, UUID.randomUUID(), occurredAt, "payload-" + type + "-" + occurredAt);
    }

    private void patientExists() {
        when(patientExistencePort.exists(PATIENT_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("merges entries from all three sources, ordered newest-first")
    void getTimeline_mergesAndSortsDescendingAcrossSources() {
        patientExists();
        TimelineEntry evaluation = entry(TimelineEventType.EVALUATION, BASE);                 // oldest
        TimelineEntry airway = entry(TimelineEventType.AIRWAY, BASE.plusHours(2));            // newest
        TimelineEntry sbt = entry(TimelineEventType.SBT, BASE.plusHours(1));                  // middle
        when(evaluationTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of(evaluation));
        when(airwayTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of(airway));
        when(sbtTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of(sbt));

        List<TimelineEntryResponse> result = service.getTimeline(PATIENT_ID);

        assertThat(result).extracting(TimelineEntryResponse::type)
                .containsExactly(TimelineEventType.AIRWAY, TimelineEventType.SBT, TimelineEventType.EVALUATION);
        assertThat(result).extracting(TimelineEntryResponse::occurredAt)
                .containsExactly(BASE.plusHours(2), BASE.plusHours(1), BASE);
        assertThat(result.get(0).payload()).isEqualTo(airway.payload());
    }

    @Test
    @DisplayName("entries sharing an occurredAt are ordered deterministically by source id")
    void getTimeline_equalTimestamps_ordersDeterministically() {
        patientExists();
        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        TimelineEntry high = new TimelineEntry(TimelineEventType.AIRWAY, highId, BASE, "high");
        TimelineEntry low = new TimelineEntry(TimelineEventType.SBT, lowId, BASE, "low");
        when(evaluationTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of());
        when(airwayTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of(high));
        when(sbtTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of(low));

        List<TimelineEntryResponse> result = service.getTimeline(PATIENT_ID);

        // Same instant → tie-break ascending by id, so the lower id comes first regardless of input order.
        assertThat(result).extracting(TimelineEntryResponse::payload).containsExactly("low", "high");
    }

    @Test
    @DisplayName("existing patient with no events returns an empty list (not 404)")
    void getTimeline_noEvents_returnsEmptyList() {
        patientExists();
        when(evaluationTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of());
        when(airwayTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of());
        when(sbtTimelinePort.findForPatient(PATIENT_ID)).thenReturn(List.of());

        assertThat(service.getTimeline(PATIENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("unknown patient throws 404 and never queries any source")
    void getTimeline_unknownPatient_throws404() {
        when(patientExistencePort.exists(PATIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getTimeline(PATIENT_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(evaluationTimelinePort, never()).findForPatient(any());
        verify(airwayTimelinePort, never()).findForPatient(any());
        verify(sbtTimelinePort, never()).findForPatient(any());
    }
}
