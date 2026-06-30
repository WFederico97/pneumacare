package wfederico.pneumacare.clinical.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port the clinical context uses to resolve a patient's current bed
 * label without depending on the patient context's JPA entities. Implemented by
 * an adapter in the patient context.
 */
public interface PatientBedLabelPort {

    /**
     * @param patientId the patient whose bed label is needed
     * @return the assigned bed number/label, or empty if the patient has no bed
     */
    Optional<String> findBedLabel(UUID patientId);
}
