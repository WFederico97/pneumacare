package wfederico.pneumacare.shared.security.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /** Count of users by enabled flag (analytics IAM). */
    long countByEnabled(boolean enabled);

    /** Per-role user counts (analytics IAM). */
    @Query("select r as role, count(u) as total from UserJpaEntity u join u.roles r group by r")
    List<RoleCount> countByRole();

    /** Projection for {@link #countByRole()}. */
    interface RoleCount {
        Role getRole();
        long getTotal();
    }
}
