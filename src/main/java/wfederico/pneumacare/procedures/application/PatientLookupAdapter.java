package wfederico.pneumacare.procedures.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link PatientLookupPort} adapter backed by the patient context's repository.
 * One of the contained dependencies from {@code procedures} on patient persistence.
 */
@Component
@RequiredArgsConstructor
public class PatientLookupAdapter implements PatientLookupPort {
    private final PatientRepository patientRepository;

    @Override
    public Optional<UUID> findIcuId(UUID patientId) {
        return patientRepository.findById(patientId)
                .map(p -> p.getIcu().getId());
    }
}
