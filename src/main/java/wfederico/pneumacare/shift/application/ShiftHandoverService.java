package wfederico.pneumacare.shift.application;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverRepository;
import wfederico.pneumacare.shift.web.dto.CreateHandoverRequest;
import wfederico.pneumacare.shift.web.dto.HandoverResponse;

import java.util.List;
import java.util.UUID;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.HANDOVER_CONTENT_EMPTY;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.HANDOVER_CONTENT_TOO_LONG;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.HANDOVER_ON_CLOSED_SHIFT;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.SHIFT_NOT_FOUND;

/**
 * Application service for shift handover notes (PNMC-92).
 *
 * <p>Lives in the shift context, so it reads the shift directly via
 * {@link MedicalShiftRepository}; the author is resolved through the shared
 * {@link CurrentUserPort}. Notes are immutable and may only be added to OPEN shifts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftHandoverService {

    private static final int MAX_CONTENT_LENGTH = 4000;

    private final ShiftHandoverRepository handoverRepository;
    private final MedicalShiftRepository shiftRepository;
    private final CurrentUserPort currentUserPort;

    /**
     * Creates a handover note on an OPEN shift.
     *
     * <ol>
     *   <li>{@code notesContent} must be non-empty and at most 4000 chars — otherwise {@code 422}.</li>
     *   <li>The shift must exist — otherwise {@code 404}.</li>
     *   <li>The shift must be OPEN — otherwise {@code 409}, with nothing written.</li>
     * </ol>
     *
     * <p>{@link Observed} records a {@code handover.create} timer/span. Only the shift
     * UUID (non-PII) is added as a span attribute — the note content is never placed on
     * a span.
     */
    @Observed(name = "handover.create", contextualName = "create-handover",
            lowCardinalityKeyValues = {"endpoint", "handover-create"})
    @Transactional
    public HandoverResponse create(UUID shiftId, CreateHandoverRequest request) {
        Span.current().setAttribute("shift.id", String.valueOf(shiftId));
        String content = request == null ? null : request.notesContent();
        if (content == null || content.isBlank()) {
            throw new BusinessLayerException(HANDOVER_CONTENT_EMPTY, HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessLayerException(HANDOVER_CONTENT_TOO_LONG, HttpStatus.UNPROCESSABLE_CONTENT);
        }

        MedicalShiftJpaEntity shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BusinessLayerException(
                        SHIFT_NOT_FOUND + shiftId, HttpStatus.NOT_FOUND));

        if (shift.getStatus() == ShiftStatus.CLOSED) {
            throw new BusinessLayerException(HANDOVER_ON_CLOSED_SHIFT, HttpStatus.CONFLICT);
        }

        ShiftHandoverJpaEntity handover = ShiftHandoverJpaEntity.builder()
                .shiftId(shiftId)
                .authorId(currentUserPort.currentUserId())
                .notesContent(content)
                .build();
        HandoverResponse response = HandoverResponse.from(handoverRepository.save(handover));

        log.info("Handover note {} added to shift {} by {}",
                response.id(), shiftId, response.authorId());
        return response;
    }

    /**
     * Returns all handover notes for a shift, newest first. {@code 404} if the shift
     * does not exist.
     */
    @Transactional(readOnly = true)
    public List<HandoverResponse> getForShift(UUID shiftId) {
        if (!shiftRepository.existsById(shiftId)) {
            throw new BusinessLayerException(SHIFT_NOT_FOUND + shiftId, HttpStatus.NOT_FOUND);
        }
        return handoverRepository.findByShiftIdOrderByCreatedAtDesc(shiftId)
                .stream()
                .map(HandoverResponse::from)
                .toList();
    }
}
