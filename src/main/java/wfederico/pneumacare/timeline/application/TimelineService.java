package wfederico.pneumacare.timeline.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.timeline.domain.TimelineEntry;
import wfederico.pneumacare.timeline.web.dto.TimelineEntryResponse;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.PATIENT_NOT_FOUND;

/**
 * Aggregates a patient's clinical events from every source context into one ordered
 * feed (PNMC-133): ventilator evaluations ({@code clinical}) plus airway events and
 * SBTs ({@code procedures}).
 *
 * <p>The service holds only context-agnostic logic — existence check, merge, sort.
 * Each source port returns {@link TimelineEntry} values already typed, timestamped,
 * and carrying their source response DTO as payload, so no source entity or
 * repository type leaks in here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineService {

    /**
     * Newest first by {@code occurredAt}; ties broken by the source id's string form
     * for a stable, intuitive order. (Comparing {@code UUID} directly would use signed
     * long comparison, which orders ids counter-intuitively.)
     */
    private static final Comparator<TimelineEntry> NEWEST_FIRST =
            Comparator.comparing(TimelineEntry::occurredAt)
                    .reversed()
                    .thenComparing(entry -> entry.id().toString());

    private final PatientExistencePort patientExistencePort;
    private final EvaluationTimelinePort evaluationTimelinePort;
    private final AirwayTimelinePort airwayTimelinePort;
    private final SbtTimelinePort sbtTimelinePort;

    /**
     * Returns the patient's merged clinical timeline, newest first.
     *
     * <p>The patient-existence check runs first, so an unknown patient yields
     * {@code 404} while an existing patient with no events yields an empty list
     * (rendered as {@code 200} + {@code []}).
     *
     * @param patientId the operational patient UUID
     * @return the ordered timeline (possibly empty)
     * @throws BusinessLayerException {@code 404} if the patient does not exist
     */
    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> getTimeline(UUID patientId) {
        if (!patientExistencePort.exists(patientId)) {
            throw new BusinessLayerException(PATIENT_NOT_FOUND + patientId, HttpStatus.NOT_FOUND);
        }

        List<TimelineEntryResponse> timeline = Stream.of(
                        evaluationTimelinePort.findForPatient(patientId),
                        airwayTimelinePort.findForPatient(patientId),
                        sbtTimelinePort.findForPatient(patientId))
                .flatMap(List::stream)
                .sorted(NEWEST_FIRST)
                .map(TimelineEntryResponse::from)
                .toList();

        log.debug("Timeline for patient {} aggregated {} event(s)", patientId, timeline.size());
        return timeline;
    }
}
