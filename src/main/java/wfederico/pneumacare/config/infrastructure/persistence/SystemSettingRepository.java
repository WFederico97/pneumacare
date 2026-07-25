package wfederico.pneumacare.config.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Spring Data repository for {@link SystemSettingJpaEntity}, keyed by setting key. */
@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSettingJpaEntity, String> {

    /** All settings ordered by category then key, for stable admin-hub rendering. */
    List<SystemSettingJpaEntity> findAllByOrderByCategoryAscSettingKeyAsc();
}
