package wfederico.pneumacare.timeline.application;

import wfederico.pneumacare.timeline.domain.TimelineEntry;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port over the {@code procedures} context's spontaneous breathing trials,
 * projected as timeline entries (type {@code SBT}, {@code occurredAt} = recorded time).
 */
public interface SbtTimelinePort {

    /** The patient's SBTs as timeline entries. */
    List<TimelineEntry> findForPatient(UUID patientId);
}
