package wfederico.pneumacare.procedures.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link PatientAirwayPort} adapter backed by the patient context's repository.
 * Contains the only dependency from {@code procedures} on patient persistence.
 *
 * <p>{@link #applyRespiratoryStatus} loads the managed entity and mutates it; the
 * change is flushed when the caller's transaction commits, so the status update
 * and the airway-event insert are atomic.
 */
@Component
@RequiredArgsConstructor
public class PatientAirwayAdapter implements PatientAirwayPort {
    private final PatientRepository patientRepository;

    @Override
    public Optional<PatientAirwayView> findAirwayView(UUID patientId) {
        return patientRepository.findById(patientId)
                .map(p -> new PatientAirwayView(
                        p.getId(),
                        p.getIcu().getId(),
                        p.getRespiratoryStatus()));
    }

    @Override
    public void applyRespiratoryStatus(UUID patientId, RespiratoryStatus newStatus) {
        PatientJpaEntity patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalStateException(
                        "Patient disappeared mid-transaction: " + patientId));
        patient.setRespiratoryStatus(newStatus);
        // dirty-checked; flushed on commit. Explicit save kept for readability.
        patientRepository.save(patient);
    }
}
