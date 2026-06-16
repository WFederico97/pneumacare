package wfederico.pneumacare.procedures.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AirwayEventRepository extends JpaRepository<AirwayEventJpaEntity, UUID> {
    /** Airway events for a patient, newest first (by clinically-reported time). */
    List<AirwayEventJpaEntity> findByPatientIdOrderByEventTimeDesc(UUID patientId);
}
