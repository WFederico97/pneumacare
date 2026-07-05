package wfederico.pneumacare.inventory.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PhysicalVentilatorRepository extends JpaRepository<PhysicalVentilatorJpaEntity, UUID> {

    boolean existsBySerialNumber(String serialNumber);

    Page<PhysicalVentilatorJpaEntity> findByIcuId(UUID icuId, Pageable pageable);

    /**
     * ICU existence check via native SQL: the ICU JPA entity belongs to the
     * patient context, so this context deliberately avoids importing it.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM intensive_care_units WHERE id = :icuId)",
            nativeQuery = true)
    boolean icuExists(@Param("icuId") UUID icuId);
}
