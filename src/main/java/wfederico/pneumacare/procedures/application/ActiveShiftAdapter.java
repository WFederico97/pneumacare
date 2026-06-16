package wfederico.pneumacare.procedures.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ActiveShiftPort} adapter backed by the shift context's repository.
 * Contains the only dependency from {@code procedures} on shift persistence.
 */
@Component
@RequiredArgsConstructor
public class ActiveShiftAdapter implements ActiveShiftPort {
    private final MedicalShiftRepository shiftRepository;

    @Override
    public Optional<UUID> findActiveShiftId(UUID icuId) {
        return shiftRepository.findByIcuIdAndStatus(icuId, ShiftStatus.OPEN)
                .map(MedicalShiftJpaEntity::getId);
    }
}
