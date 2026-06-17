package wfederico.pneumacare.timeline.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.procedures.web.dto.SbtResponse;
import wfederico.pneumacare.timeline.domain.TimelineEntry;
import wfederico.pneumacare.timeline.domain.TimelineEventType;

import java.util.List;
import java.util.UUID;

/**
 * {@link SbtTimelinePort} adapter backed by the procedures context. Maps each SBT to
 * a {@link TimelineEntry} whose {@code occurredAt} is the recorded time
 * ({@code created_at}) and whose payload is the existing {@link SbtResponse}.
 */
@Component
@RequiredArgsConstructor
public class SbtTimelineAdapter implements SbtTimelinePort {

    private final SbtRepository sbtRepository;

    @Override
    public List<TimelineEntry> findForPatient(UUID patientId) {
        return sbtRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(e -> new TimelineEntry(
                        TimelineEventType.SBT,
                        e.getId(),
                        e.getCreatedAt(),
                        SbtResponse.from(e)))
                .toList();
    }
}
