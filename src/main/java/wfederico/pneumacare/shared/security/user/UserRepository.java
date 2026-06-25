package wfederico.pneumacare.shared.security.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserJpaEntity}.
 *
 * <p>{@link #findByUsername(String)} backs both the bootstrap seeder's idempotency
 * check and (later) authentication lookups.
 */
@Repository
public interface UserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByUsername(String username);
}
