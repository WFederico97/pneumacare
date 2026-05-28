package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PatientIdentityJpaEntity}.
 *
 * <p>All reads transparently decrypt PII fields via {@link
 * wfederico.pneumacare.shared.security.encryption.AesAttributeConverter}.
 * All writes transparently encrypt them. Callers interact only with plain text.
 *
 * <p>{@link #findById(UUID)} uses an {@link EntityGraph} to fetch
 * {@code identifiers} and their {@code patientIdentifierType} in a single
 * JOIN query, avoiding the N+1 problem when mapping to {@code PatientResponse}.
 */
@Repository
public interface PatientIdentityRepository extends JpaRepository<PatientIdentityJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"identifiers", "identifiers.patientIdentifierType"})
    @Override
    Optional<PatientIdentityJpaEntity> findById(UUID id);
}
