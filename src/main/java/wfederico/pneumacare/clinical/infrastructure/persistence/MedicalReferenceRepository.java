package wfederico.pneumacare.clinical.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MedicalReferenceRepository extends JpaRepository<MedicalReferenceJpaEntity, UUID> {

    /** Looks up the single reference entry for a metric's interpretation band. */
    Optional<MedicalReferenceJpaEntity> findByMetricAndBand(String metric, String band);
}
