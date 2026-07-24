package wfederico.pneumacare.clinical.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.patient.application.BedAlertStatusPort;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Clinical-context adapter for {@link BedAlertStatusPort}. Resolves the current
 * alert state of each patient from their most recent evaluation.
 *
 * <p>Iterates patient-by-patient rather than issuing a single grouped query: the
 * occupied-bed set per ICU is small (tens at most), so N cheap indexed lookups
 * keep the code simple and avoid a bespoke aggregate query.
 */
@Component
@RequiredArgsConstructor
public class ClinicalBedAlertAdapter implements BedAlertStatusPort {

    private final EvaluationRepository evaluationRepository;

    @Override
    public Set<UUID> patientsWithActiveAlert(Collection<UUID> patientIds) {
        Set<UUID> flagged = new HashSet<>();
        for (UUID patientId : patientIds) {
            evaluationRepository.findFirstByPatientIdOrderByEvaluationTimeDesc(patientId)
                    .filter(EvaluationJpaEntity::isAlertTriggered)
                    .ifPresent(evaluation -> flagged.add(patientId));
        }
        return flagged;
    }
}
