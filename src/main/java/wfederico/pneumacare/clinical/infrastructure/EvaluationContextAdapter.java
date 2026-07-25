package wfederico.pneumacare.clinical.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.application.EvaluationContextPort;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link EvaluationContextPort} adapter backed by the patient and shift
 * repositories. Contains the only dependency from {@code clinical} on those
 * contexts' persistence.
 */
@Component
@RequiredArgsConstructor
public class EvaluationContextAdapter implements EvaluationContextPort {

    private final PatientRepository patientRepository;
    private final MedicalShiftRepository shiftRepository;

    @Override
    public Optional<PatientEpisode> findEpisode(UUID patientId) {
        return patientRepository.findById(patientId)
                .map(p -> new PatientEpisode(
                        p.getIcu().getId(),
                        p.getClinicalStatus() == ClinicalStatus.ADMITTED));
    }

    @Override
    public Optional<UUID> findActiveShiftId(UUID icuId) {
        return shiftRepository.findByIcuIdAndStatus(icuId, ShiftStatus.OPEN)
                .map(MedicalShiftJpaEntity::getId);
    }
}
