package wfederico.pneumacare.timeline.application;

import wfederico.pneumacare.timeline.domain.TimelineEntry;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port over the {@code procedures} context's airway events, projected as
 * timeline entries (type {@code AIRWAY}, {@code occurredAt} = clinical event time).
 */
public interface AirwayTimelinePort {

    /** The patient's airway events as timeline entries. */
    List<TimelineEntry> findForPatient(UUID patientId);
}
