package wfederico.pneumacare.timeline.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.clinical.web.dto.EvaluationResponse;
import wfederico.pneumacare.timeline.domain.TimelineEntry;
import wfederico.pneumacare.timeline.domain.TimelineEventType;

import java.util.List;
import java.util.UUID;

/**
 * {@link EvaluationTimelinePort} adapter backed by the clinical context. Maps each
 * evaluation to a {@link TimelineEntry} whose {@code occurredAt} is the evaluation
 * time and whose payload is the existing {@link EvaluationResponse}.
 */
@Component
@RequiredArgsConstructor
public class EvaluationTimelineAdapter implements EvaluationTimelinePort {

    private final EvaluationRepository evaluationRepository;

    @Override
    public List<TimelineEntry> findForPatient(UUID patientId) {
        return evaluationRepository.findByPatientIdOrderByEvaluationTimeDesc(patientId)
                .stream()
                .map(e -> new TimelineEntry(
                        TimelineEventType.EVALUATION,
                        e.getId(),
                        e.getEvaluationTime(),
                        EvaluationResponse.from(e)))
                .toList();
    }
}
