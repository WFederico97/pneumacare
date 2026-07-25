package wfederico.pneumacare.shift.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShiftHandoverRepository extends JpaRepository<ShiftHandoverJpaEntity, UUID> {

    /** Handover notes for a shift, newest first (by created_at). */
    List<ShiftHandoverJpaEntity> findByShiftIdOrderByCreatedAtDesc(UUID shiftId);

    /** Number of handover notes on a shift (shift-history summary). */
    long countByShiftId(UUID shiftId);
}
