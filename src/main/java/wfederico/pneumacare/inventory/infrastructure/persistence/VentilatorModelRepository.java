package wfederico.pneumacare.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VentilatorModelRepository extends JpaRepository<VentilatorModelJpaEntity, UUID> {

    /** Resolves a model row by brand string + model name (exact match). */
    Optional<VentilatorModelJpaEntity> findFirstByBrandAndModel(String brand, String model);
}
