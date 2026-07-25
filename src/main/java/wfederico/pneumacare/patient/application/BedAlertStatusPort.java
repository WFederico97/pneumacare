package wfederico.pneumacare.patient.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port the patient context uses to learn which occupying patients
 * currently have an active clinical alert, without depending on the clinical
 * context's JPA entities. Implemented by an adapter in the clinical context.
 *
 * <p>An "active alert" is defined as the patient's most recent evaluation having
 * tripped a clinical threshold ({@code alert_triggered = true}).
 */
public interface BedAlertStatusPort {

    /**
     * @param patientIds occupying patients to check (may be empty)
     * @return the subset of {@code patientIds} whose latest evaluation triggered an alert
     */
    Set<UUID> patientsWithActiveAlert(Collection<UUID> patientIds);
}
