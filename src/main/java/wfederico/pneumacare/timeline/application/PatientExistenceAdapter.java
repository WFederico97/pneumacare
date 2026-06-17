package wfederico.pneumacare.timeline.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;

import java.util.UUID;

/**
 * {@link PatientExistencePort} adapter backed by the patient context's repository.
 * Uses {@code existsById} (a cheap count) rather than {@code findById}, which would
 * eagerly load the full patient graph.
 */
@Component
@RequiredArgsConstructor
public class PatientExistenceAdapter implements PatientExistencePort {

    private final PatientRepository patientRepository;

    @Override
    public boolean exists(UUID patientId) {
        return patientRepository.existsById(patientId);
    }
}
