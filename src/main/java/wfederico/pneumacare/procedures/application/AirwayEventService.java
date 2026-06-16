package wfederico.pneumacare.procedures.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;
import wfederico.pneumacare.procedures.application.PatientAirwayPort.PatientAirwayView;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventJpaEntity;
import wfederico.pneumacare.procedures.infrastructure.persistence.AirwayEventRepository;
import wfederico.pneumacare.procedures.web.dto.AirwayEventResponse;
import wfederico.pneumacare.procedures.web.dto.CreateAirwayEventRequest;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;

import java.util.List;
import java.util.UUID;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.ILLEGAL_AIRWAY_TRANSITION;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.NO_OPEN_SHIFT;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.PATIENT_NOT_FOUND;

/**
 * Application service for airway-event registration and history (PNMC-94).
 *
 * <p>Depends only on its own ports ({@link PatientAirwayPort}, {@link ActiveShiftPort},
 * {@link CurrentUserPort}) and its repository — no patient/shift JPA types or Spring
 * Security types leak in here. The state machine itself lives on {@link AirwayEventType}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirwayEventService {

    private final AirwayEventRepository airwayEventRepository;
    private final PatientAirwayPort patientAirwayPort;
    private final ActiveShiftPort activeShiftPort;
    private final CurrentUserPort currentUserPort;

    /**
     * Registers an airway event and atomically advances the patient's respiratory
     * status. Either both writes happen or neither does (single transaction):
     *
     * <ol>
     *   <li>Patient must exist — otherwise {@code 404}.</li>
     *   <li>The patient's ICU must have an OPEN shift — otherwise {@code 409}.</li>
     *   <li>The event must be a legal transition from the current status — otherwise
     *       {@code 409}, with nothing written and the status unchanged.</li>
     * </ol>
     */
    @Transactional
    public AirwayEventResponse register(CreateAirwayEventRequest request) {
        PatientAirwayView patient = patientAirwayPort.findAirwayView(request.patientId())
                .orElseThrow(() -> new BusinessLayerException(
                        PATIENT_NOT_FOUND + request.patientId(), HttpStatus.NOT_FOUND));

        UUID shiftId = activeShiftPort.findActiveShiftId(patient.icuId())
                .orElseThrow(() -> new BusinessLayerException(
                        NO_OPEN_SHIFT, HttpStatus.CONFLICT));

        AirwayEventType eventType = request.eventType();
        RespiratoryStatus current = patient.respiratoryStatus();
        if (!eventType.isAllowedFrom(current)) {
            throw new BusinessLayerException(
                    ILLEGAL_AIRWAY_TRANSITION + ": " + eventType
                            + " requiere estado " + eventType.requiredCurrentStatus()
                            + " (estado actual: " + current + ")",
                    HttpStatus.CONFLICT);
        }
        RespiratoryStatus resulting = eventType.resultingStatus();

        AirwayEventJpaEntity event = AirwayEventJpaEntity.builder()
                .patientId(patient.patientId())
                .shiftId(shiftId)
                .eventTime(request.eventTimestamp())
                .eventType(eventType)
                .createdBy(currentUserPort.currentUserId())
                .build();
        AirwayEventJpaEntity saved = airwayEventRepository.save(event);

        patientAirwayPort.applyRespiratoryStatus(patient.patientId(), resulting);

        log.info("Airway event {} registered for patient {} (shift {}): {} -> {}",
                saved.getId(), patient.patientId(), shiftId, current, resulting);
        return AirwayEventResponse.from(saved);
    }

    /**
     * Returns the patient's airway events, newest first. {@code 404} if the patient
     * does not exist.
     */
    @Transactional(readOnly = true)
    public List<AirwayEventResponse> getPatientAirwayEvents(UUID patientId) {
        if (patientAirwayPort.findAirwayView(patientId).isEmpty()) {
            throw new BusinessLayerException(PATIENT_NOT_FOUND + patientId, HttpStatus.NOT_FOUND);
        }
        return airwayEventRepository.findByPatientIdOrderByEventTimeDesc(patientId)
                .stream()
                .map(AirwayEventResponse::from)
                .toList();
    }
}
