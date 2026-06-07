package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IcuJpaEntity}.
 *
 * <p>Used by the admission service to validate that the ICU supplied in the
 * admission request exists before persisting the patient row.
 */
@Repository
public interface IcuRepository extends JpaRepository<IcuJpaEntity, UUID> {
}
