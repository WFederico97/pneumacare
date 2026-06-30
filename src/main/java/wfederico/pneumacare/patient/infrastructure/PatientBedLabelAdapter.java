package wfederico.pneumacare.patient.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.application.PatientBedLabelPort;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Patient-context implementation of {@link PatientBedLabelPort}. Resolves the
 * patient's current bed label via a single projection query.
 */
@Component
@RequiredArgsConstructor
public class PatientBedLabelAdapter implements PatientBedLabelPort {

    private final PatientRepository patientRepository;

    @Override
    public Optional<String> findBedLabel(UUID patientId) {
        return patientRepository.findBedLabelByPatientId(patientId);
    }
}
