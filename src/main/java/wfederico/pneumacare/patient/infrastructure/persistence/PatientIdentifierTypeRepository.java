package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PatientIdentifierTypeJpaEntity}.
 *
 * <p>Used by the application layer to look up identifier types (DNI, CUIL,
 * Passport, etc.) by their primary key when registering a new patient.
 */
@Repository
public interface PatientIdentifierTypeRepository
        extends JpaRepository<PatientIdentifierTypeJpaEntity, Integer> {
}
