package wfederico.pneumacare.procedures.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port over the patient aggregate, exposing only what procedures features
 * need that are not airway-specific: the patient's ICU and whether the episode is
 * still open. Keeps {@code PatientJpaEntity} out of the procedures application
 * layer.
 */
public interface PatientLookupPort {

    /** The patient's episode summary, or empty if the patient does not exist. */
    Optional<PatientEpisodeView> findEpisode(UUID patientId);

    /**
     * Minimal read model: which ICU the episode belongs to, and whether it is
     * still ADMITTED. A closed episode exists but must not accept clinical writes.
     */
    record PatientEpisodeView(UUID icuId, boolean episodeOpen) {}
}
