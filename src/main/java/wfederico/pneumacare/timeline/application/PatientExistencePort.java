package wfederico.pneumacare.timeline.application;

import java.util.UUID;

/**
 * Outbound port over the patient aggregate, exposing only an existence check —
 * all the timeline needs to distinguish an unknown patient ({@code 404}) from one
 * with no events ({@code 200} + empty list). Keeps {@code PatientJpaEntity} out of
 * the timeline application layer.
 */
public interface PatientExistencePort {

    /** {@code true} if an operational patient with this id exists. */
    boolean exists(UUID patientId);
}
