package wfederico.pneumacare.procedures.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AirwayEventRepository extends JpaRepository<AirwayEventJpaEntity, UUID> {
    /** Airway events for a patient, newest first (by clinically-reported time). */
    List<AirwayEventJpaEntity> findByPatientIdOrderByEventTimeDesc(UUID patientId);

    /**
     * All airway events grouped by patient and ordered chronologically, so the
     * analytics read-model can fold each patient's events into invasive-ventilation
     * intervals in a single pass.
     */
    List<AirwayEventJpaEntity> findAllByOrderByPatientIdAscEventTimeAsc();

    /** Airway-event counts grouped by shift, for the shift-history activity summary. */
    @Query("select x.shiftId as shiftId, count(x) as total from AirwayEventJpaEntity x "
            + "where x.shiftId in :shiftIds group by x.shiftId")
    List<ShiftCount> countByShiftIds(java.util.Collection<java.util.UUID> shiftIds);

    /** Projection for {@link #countByShiftIds}. */
    interface ShiftCount {
        java.util.UUID getShiftId();
        long getTotal();
    }
}
