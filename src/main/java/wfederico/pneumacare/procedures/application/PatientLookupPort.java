package wfederico.pneumacare.procedures.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port over the patient aggregate, exposing only what procedures features
 * need that are not airway-specific: the patient's ICU (which doubles as an
 * existence check). Keeps {@code PatientJpaEntity} out of the procedures
 * application layer.
 */
public interface PatientLookupPort {

    /** The patient's ICU id, or empty if the patient does not exist. */
    Optional<UUID> findIcuId(UUID patientId);
}
