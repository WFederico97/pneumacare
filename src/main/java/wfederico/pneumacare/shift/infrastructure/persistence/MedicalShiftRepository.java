package wfederico.pneumacare.shift.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MedicalShiftRepository extends JpaRepository<MedicalShiftJpaEntity, UUID> {
    boolean existsByIcuIdAndStatus(UUID icuId, ShiftStatus shiftStatus);
    Optional<MedicalShiftJpaEntity> findByIcuIdAndStatus(UUID icuId, ShiftStatus shiftStatus);

    /** True when any shift is currently in the given status (analytics ward). */
    boolean existsByStatus(ShiftStatus status);

    /** Count of shifts started since the given instant (analytics ward). */
    long countByStartTimeAfter(OffsetDateTime since);
}
