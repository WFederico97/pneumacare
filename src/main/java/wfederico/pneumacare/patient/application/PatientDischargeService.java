package wfederico.pneumacare.patient.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.inventory.application.AssetAssignmentService;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Closes a patient episode: sets the terminus (discharge date + disposition),
 * derives the lifecycle status, frees the bed, releases any active ventilator
 * assignment, and publishes {@link PatientDischargedEvent}. One transaction —
 * any failure rolls the whole discharge back. Envers audits the row transition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientDischargeService {

    private final PatientRepository patientRepository;
    private final IcuBedRepository bedRepository;
    private final AssetAssignmentService assetAssignmentService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Discharges an admitted patient.
     *
     * @param patientId     the episode UUID ({@code patients.id})
     * @param disposition   clinical disposition of the closure
     * @param dischargeDate closure instant; null defaults to now (UTC)
     * @return the closed episode
     * @throws BusinessLayerException 404 unknown patient; 409 episode not open
     *         or airway guard tripped; 400 invalid discharge date
     */
    @Transactional
    public PatientJpaEntity discharge(UUID patientId, Disposition disposition, OffsetDateTime dischargeDate) {
        PatientJpaEntity patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessLayerException(
                        "No se encontró el paciente con id: " + patientId, HttpStatus.NOT_FOUND));

        if (patient.getClinicalStatus() != ClinicalStatus.ADMITTED) {
            throw new BusinessLayerException(
                    "El episodio ya fue cerrado", HttpStatus.CONFLICT);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime effectiveDate = dischargeDate == null ? now : dischargeDate;
        if (effectiveDate.isBefore(patient.getAdmissionDate())) {
            throw new BusinessLayerException(
                    "La fecha de egreso no puede ser anterior a la admisión", HttpStatus.BAD_REQUEST);
        }
        if (effectiveDate.isAfter(now)) {
            throw new BusinessLayerException(
                    "La fecha de egreso no puede ser futura", HttpStatus.BAD_REQUEST);
        }

        // Airway guard: HOME/WARD require a secured natural airway first. Death,
        // withdrawal and external transfer legitimately occur on the ventilator.
        boolean artificialAirway = patient.getRespiratoryStatus() == RespiratoryStatus.INTUBATED
                || patient.getRespiratoryStatus() == RespiratoryStatus.TRACHEOSTOMY;
        boolean wardBound = disposition == Disposition.HOME || disposition == Disposition.WARD;
        if (artificialAirway && wardBound) {
            throw new BusinessLayerException(
                    "El paciente tiene vía aérea artificial: registre la extubación antes del egreso",
                    HttpStatus.CONFLICT);
        }

        patient.setClinicalStatus(disposition.toClinicalStatus());
        patient.setDisposition(disposition);
        patient.setDischargeDate(effectiveDate);

        IcuBedJpaEntity bed = patient.getBed();
        if (bed != null) {
            bed.setStatus(BedStatus.AVAILABLE);
            patient.setBed(null);
            bedRepository.save(bed);
        }

        assetAssignmentService.releaseForPatient(patientId);
        patientRepository.save(patient);

        log.info("Episode closed: patientId={}, disposition={}, status={}",
                patientId, disposition, patient.getClinicalStatus());
        eventPublisher.publishEvent(new PatientDischargedEvent(patientId, disposition, effectiveDate));
        return patient;
    }
}
