package wfederico.pneumacare.timeline.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.web.dto.AirwayEventResponse;
import wfederico.pneumacare.timeline.domain.TimelineEntry;
import wfederico.pneumacare.timeline.domain.TimelineEventType;

import java.util.List;
import java.util.UUID;

/**
 * {@link AirwayTimelinePort} adapter backed by the procedures context. Maps each
 * airway event to a {@link TimelineEntry} whose {@code occurredAt} is the
 * clinically-reported event time and whose payload is the existing
 * {@link AirwayEventResponse}.
 */
@Component
@RequiredArgsConstructor
public class AirwayTimelineAdapter implements AirwayTimelinePort {

    private final AirwayEventRepository airwayEventRepository;

    @Override
    public List<TimelineEntry> findForPatient(UUID patientId) {
        return airwayEventRepository.findByPatientIdOrderByEventTimeDesc(patientId)
                .stream()
                .map(e -> new TimelineEntry(
                        TimelineEventType.AIRWAY,
                        e.getId(),
                        e.getEventTime(),
                        AirwayEventResponse.from(e)))
                .toList();
    }
}
