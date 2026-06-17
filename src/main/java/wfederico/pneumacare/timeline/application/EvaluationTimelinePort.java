package wfederico.pneumacare.timeline.application;

import wfederico.pneumacare.timeline.domain.TimelineEntry;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port over the {@code clinical} context's evaluations, projected as
 * timeline entries (type {@code EVALUATION}, {@code occurredAt} = evaluation time).
 */
public interface EvaluationTimelinePort {

    /** The patient's evaluations as timeline entries (ordering is irrelevant; the service re-sorts). */
    List<TimelineEntry> findForPatient(UUID patientId);
}
