package wfederico.pneumacare.procedures.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.procedures.application.PatientLookupPort.PatientEpisodeView;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtJpaEntity;
import wfederico.pneumacare.procedures.infrastructure.persistence.SbtRepository;
import wfederico.pneumacare.procedures.web.dto.CreateSbtRequest;
import wfederico.pneumacare.procedures.web.dto.SbtResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;

import java.util.List;
import java.util.UUID;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.EPISODE_CLOSED;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.NO_OPEN_SHIFT;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.PATIENT_NOT_FOUND;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.SBT_DURATION_NOT_POSITIVE;

/**
 * Application service for SBT (Spontaneous Breathing Trial) recording and history
 * (PNMC-95).
 *
 * <p>Depends only on its own ports ({@link PatientLookupPort}, {@link ActiveShiftPort})
 * plus the shared {@link CurrentUserPort} and its repository — no patient/shift JPA
 * types or Spring Security types leak in here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SbtService {

    private final SbtRepository sbtRepository;
    private final PatientLookupPort patientLookupPort;
    private final ActiveShiftPort activeShiftPort;
    private final CurrentUserPort currentUserPort;

    /**
     * Records an SBT result tied to the patient's OPEN shift.
     *
     * <ol>
     *   <li>{@code durationMinutes} must be a positive integer — otherwise {@code 422}.</li>
     *   <li>Patient must exist — otherwise {@code 404}.</li>
     *   <li>The episode must still be open (ADMITTED) — otherwise {@code 409}.</li>
     *   <li>The patient's ICU must have an OPEN shift (the trial is linked to it) —
     *       otherwise {@code 409}.</li>
     * </ol>
     */
    @Transactional
    public SbtResponse register(CreateSbtRequest request) {
        if (request.durationMinutes() == null || request.durationMinutes() <= 0) {
            throw new BusinessLayerException(SBT_DURATION_NOT_POSITIVE, HttpStatus.UNPROCESSABLE_CONTENT);
        }

        PatientEpisodeView episode = patientLookupPort.findEpisode(request.patientId())
                .orElseThrow(() -> new BusinessLayerException(
                        PATIENT_NOT_FOUND + request.patientId(), HttpStatus.NOT_FOUND));

        if (!episode.episodeOpen()) {
            throw new BusinessLayerException(EPISODE_CLOSED, HttpStatus.CONFLICT);
        }

        UUID shiftId = activeShiftPort.findActiveShiftId(episode.icuId())
                .orElseThrow(() -> new BusinessLayerException(
                        NO_OPEN_SHIFT, HttpStatus.CONFLICT));

        SbtJpaEntity sbt = SbtJpaEntity.builder()
                .patientId(request.patientId())
                .shiftId(shiftId)
                .durationMinutes(request.durationMinutes())
                .toleranceResult(request.toleranceResult())
                .createdBy(currentUserPort.currentUserId())
                .build();
        SbtJpaEntity saved = sbtRepository.save(sbt);

        log.info("SBT {} recorded for patient {} (shift {}): {} after {} min",
                saved.getId(), saved.getPatientId(), shiftId,
                saved.getToleranceResult(), saved.getDurationMinutes());
        return SbtResponse.from(saved);
    }

    /**
     * Returns the patient's SBT history, newest first. {@code 404} if the patient
     * does not exist.
     */
    @Transactional(readOnly = true)
    public List<SbtResponse> getHistory(UUID patientId) {
        if (patientLookupPort.findEpisode(patientId).isEmpty()) {
            throw new BusinessLayerException(PATIENT_NOT_FOUND + patientId, HttpStatus.NOT_FOUND);
        }
        return sbtRepository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(SbtResponse::from)
                .toList();
    }
}
