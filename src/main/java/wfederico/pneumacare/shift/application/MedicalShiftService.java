package wfederico.pneumacare.shift.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.CurrentUserPort;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.web.dto.CreateShiftRequest;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final IcuExistencePort icuExistencePort;
    private final CurrentUserPort currentUserPort;
    private final CurrentIcuPort currentIcuPort;

    /**
     * Returns the active (OPEN) shift for the current context's ICU, if any.
     */
    @Transactional(readOnly = true)
    public Optional<ShiftResponse> getActiveShift(){
        UUID icuId = currentIcuPort.currentIcuId();
        return shiftRepository.findByIcuIdAndStatus(icuId,ShiftStatus.OPEN)
                .map(ShiftResponse::from);
    }/**
     * Opens a new shift for an ICU.
     */
    @Transactional
    public ShiftResponse open(CreateShiftRequest shiftRequest){
        UUID icuId = shiftRequest.icuId();

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
            return ShiftResponse.from(shiftRepository.save(shift));
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent open detected for icuId={}, translated to 409.", icuId);
            throw new BusinessLayerException(SHIFT_ALREADY_OPEN_FOR_ICU,HttpStatus.CONFLICT);
        }
    }

    /**
     * Closes an OPEN shift.
     */
    @Transactional
    public ShiftResponse close(UUID icuId){
        MedicalShiftJpaEntity shift = shiftRepository.findById(icuId)
                .orElseThrow(() -> new BusinessLayerException(
                        SHIFT_NOT_FOUND,HttpStatus.NOT_FOUND
                ));
        if (shift.getStatus() == ShiftStatus.CLOSED){
            throw new BusinessLayerException(SHIFT_ALREADY_CLOSED, HttpStatus.CONFLICT);
        }

        shift.setStatus(ShiftStatus.CLOSED);
        shift.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
        return ShiftResponse.from(shiftRepository.save(shift));
    }
}
