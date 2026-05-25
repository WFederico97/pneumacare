package wfederico.pneumacare.patient.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PatientIdentityJpaEntity}.
 *
 * <p>All reads transparently decrypt PII fields via {@link
 * wfederico.pneumacare.shared.security.encryption.AesAttributeConverter}.
 * All writes transparently encrypt them. Callers interact only with plain text.
 */
@Repository
public interface PatientIdentityRepository extends JpaRepository<PatientIdentityJpaEntity, UUID> {
}
