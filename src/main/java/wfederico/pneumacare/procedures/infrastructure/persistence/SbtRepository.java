package wfederico.pneumacare.procedures.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SbtRepository extends JpaRepository<SbtJpaEntity, UUID> {

    /** A patient's SBT history, newest first (by recorded time = created_at). */
    List<SbtJpaEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
