package wfederico.pneumacare.shift.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import wfederico.pneumacare.shift.domain.ShiftStatus;

import java.util.UUID;

public interface MedicalShiftRepository extends JpaRepository<MedicalShiftJpaEntity, UUID> {
    boolean existsByIcuIdAndStatus(UUID icuId, ShiftStatus shiftStatus);
}
