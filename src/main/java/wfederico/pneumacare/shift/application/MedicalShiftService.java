package wfederico.pneumacare.shift.application;

import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.application.ShiftActivityPort.ShiftActivity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverRepository;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;
import wfederico.pneumacare.shift.web.dto.ShiftSummaryResponse;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.ICU_NOT_FOUND;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.SHIFT_ALREADY_CLOSED;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.SHIFT_ALREADY_OPEN_FOR_ICU;
import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.SHIFT_NOT_FOUND;

/**
 * Application service for the medical shift lifecycle (open / close).
 *
 * <p>Depends only on its own ports ({@link IcuExistencePort}, {@link CurrentUserPort})
 * and its repository — no patient-context or Spring Security types leak in here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalShiftService {
    private final MedicalShiftRepository shiftRepository;
    private final ShiftHandoverRepository handoverRepository;
    private final IcuExistencePort icuExistencePort;
    private final CurrentUserPort currentUserPort;
    private final CurrentIcuPort currentIcuPort;
    private final ShiftActivityPort shiftActivityPort;

    /**
     * Returns the active (OPEN) shift for the current context's ICU, if any.
     */
    @Transactional(readOnly = true)
    public Optional<ShiftResponse> getActiveShift(){
        UUID icuId = currentIcuPort.currentIcuId();
        return shiftRepository.findByIcuIdAndStatus(icuId,ShiftStatus.OPEN)
                .map(ShiftResponse::from);
    }

    /**
     * The session ICU's shift history, newest first, each row carrying its
     * duration and the clinical activity recorded during it.
     *
     * <p>Scoped to the session ICU for the same reason {@link #close(UUID)} is:
     * a chief of guard has no business reading another unit's shift log.
     */
    @Transactional(readOnly = true)
    public List<ShiftSummaryResponse> history() {
        UUID icuId = currentIcuPort.currentIcuId();
        List<MedicalShiftJpaEntity> shifts = shiftRepository.findByIcuIdOrderByStartTimeDesc(icuId);

        List<UUID> shiftIds = shifts.stream().map(MedicalShiftJpaEntity::getId).toList();
        Map<UUID, ShiftActivity> activity = shiftActivityPort.countByShiftIds(shiftIds);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return shifts.stream()
                .map(shift -> {
                    // An OPEN shift is still running, so measure it up to now.
                    OffsetDateTime end = shift.getEndTime() == null ? now : shift.getEndTime();
                    ShiftActivity counts = activity.getOrDefault(shift.getId(), ShiftActivity.NONE);
                    return new ShiftSummaryResponse(
                            shift.getId(),
                            shift.getIcuId(),
                            shift.getChiefUserId(),
                            shift.getStatus(),
                            shift.getStartTime(),
                            shift.getEndTime(),
                            Duration.between(shift.getStartTime(), end).toMinutes(),
                            handoverRepository.countByShiftId(shift.getId()),
                            counts.evaluations(),
                            counts.airwayEvents(),
                            counts.sbts());
                })
                .toList();
    }

    /**
     * Opens a new shift for the current session's ICU.
     *
     * <p>The ICU is derived server-side from the authenticated principal via
     * {@link CurrentIcuPort} — the same source {@link #getActiveShift()} reads —
     * so open and active-lookup are always scoped to the same ICU. The client
     * does not supply it.
     *
     * <p>{@link Observed} records a {@code shift.open} timer/span; only the ICU UUID
     * (non-PII) is added as a span attribute.
     */
    @Observed(name = "shift.open", contextualName = "open-shift",
            lowCardinalityKeyValues = {"endpoint", "shift-open"})
    @Transactional
    public ShiftResponse open(){
        UUID icuId = currentIcuPort.currentIcuId();
        Span.current().setAttribute("shift.icu_id", String.valueOf(icuId));

        if (!icuExistencePort.exists(icuId)){
            throw new BusinessLayerException(ICU_NOT_FOUND + icuId, HttpStatus.UNPROCESSABLE_CONTENT);
        }

        if (shiftRepository.existsByIcuIdAndStatus(icuId,ShiftStatus.OPEN)){
            throw new BusinessLayerException(SHIFT_ALREADY_OPEN_FOR_ICU, HttpStatus.CONFLICT);
        }

        MedicalShiftJpaEntity shift = MedicalShiftJpaEntity.builder()
                .icuId(icuId)
                .chiefUserId(currentUserPort.currentUserId())
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build();
        try {
            MedicalShiftJpaEntity saved = shiftRepository.save(shift);
            Span.current().setAttribute("shift.id", String.valueOf(saved.getId()));
            return ShiftResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent open detected for icuId={}, translated to 409.", icuId);
            throw new BusinessLayerException(SHIFT_ALREADY_OPEN_FOR_ICU,HttpStatus.CONFLICT);
        }
    }

    /**
     * Closes an OPEN shift belonging to the caller's session ICU.
     *
     * <p>The shift must be in the ICU derived from the authenticated principal
     * ({@link CurrentIcuPort}) — the same source {@link #open()} uses. Without
     * that check a chief of guard could close another ICU's shift simply by
     * knowing its UUID. A shift in a different ICU is reported as
     * {@code 404}, not {@code 403}, so the endpoint does not confirm the
     * existence of shifts the caller may not act on.
     *
     * <p>{@link Observed} records a {@code shift.close} timer/span; only the shift UUID
     * (non-PII) is added as a span attribute.
     */
    @Observed(name = "shift.close", contextualName = "close-shift",
            lowCardinalityKeyValues = {"endpoint", "shift-close"})
    @Transactional
    public ShiftResponse close(UUID shiftId){
        Span.current().setAttribute("shift.id", String.valueOf(shiftId));
        MedicalShiftJpaEntity shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BusinessLayerException(
                        SHIFT_NOT_FOUND,HttpStatus.NOT_FOUND
                ));

        UUID sessionIcuId = currentIcuPort.currentIcuId();
        if (!shift.getIcuId().equals(sessionIcuId)) {
            log.warn("Cross-ICU shift close rejected: shiftId={}, shiftIcu={}, sessionIcu={}",
                    shiftId, shift.getIcuId(), sessionIcuId);
            throw new BusinessLayerException(SHIFT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (shift.getStatus() == ShiftStatus.CLOSED){
            throw new BusinessLayerException(SHIFT_ALREADY_CLOSED, HttpStatus.CONFLICT);
        }

        shift.setStatus(ShiftStatus.CLOSED);
        shift.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
        return ShiftResponse.from(shiftRepository.save(shift));
    }
}
