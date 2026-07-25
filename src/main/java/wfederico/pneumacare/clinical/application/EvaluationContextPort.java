package wfederico.pneumacare.clinical.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port resolving the clinical context an evaluation must be attached to:
 * the patient's episode and its ICU's OPEN shift. Keeps {@code PatientJpaEntity}
 * and {@code MedicalShiftJpaEntity} out of the clinical application layer.
 *
 * <p>The shift is resolved <strong>server-side</strong> rather than accepted from
 * the client: a caller-supplied {@code shiftId} could attach a reading to a closed
 * shift, or to a shift belonging to another ICU entirely.
 */
public interface EvaluationContextPort {

    /** The patient's episode summary, or empty if the patient does not exist. */
    Optional<PatientEpisode> findEpisode(UUID patientId);

    /** The id of the ICU's OPEN shift, or empty when no shift is open. */
    Optional<UUID> findActiveShiftId(UUID icuId);

    /**
     * Minimal read model: which ICU the episode belongs to, and whether it is
     * still ADMITTED. A closed episode exists but must not accept clinical writes.
     */
    record PatientEpisode(UUID icuId, boolean episodeOpen) {}
}
