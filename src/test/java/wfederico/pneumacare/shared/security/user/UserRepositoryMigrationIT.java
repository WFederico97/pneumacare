package wfederico.pneumacare.shared.security.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wfederico.pneumacare.TestcontainersConfiguration;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies V15 reshaped the user/role schema and that {@link UserJpaEntity}
 * round-trips canonical roles, while the database rejects non-canonical ones.
 *
 * <p>Uses the staging profile so Flyway runs all migrations (V1–V15) against a
 * fresh Testcontainers Postgres instance, then Hibernate validates the schema.
 *
 * <p>Disabled by convention; run individually:
 *   mvnw.cmd test -Dtest=UserRepositoryMigrationIT
 */
@Disabled("Integration test — run individually with -Dtest=UserRepositoryMigrationIT")
@SpringBootTest(
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("staging")
class UserRepositoryMigrationIT {

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    @Test
    void persistsAndReadsBackAUserWithCanonicalRoles() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        UserJpaEntity user = UserJpaEntity.builder()
                .username("therapist1")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .displayName("Test Therapist")
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_THERAPIST, Role.ROLE_COMPLIANCE))
                .build();

        userRepository.saveAndFlush(user);
        entityManager.clear();

        UserJpaEntity found = userRepository.findByUsername("therapist1").orElseThrow();
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getDisplayName()).isEqualTo("Test Therapist");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getRoles())
                .containsExactlyInAnyOrder(Role.ROLE_THERAPIST, Role.ROLE_COMPLIANCE);
    }

    @Test
    void databaseRejectsNonCanonicalRoleString() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        UserJpaEntity user = UserJpaEntity.builder()
                .username("baduser")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .enabled(true)
                .roles(EnumSet.noneOf(Role.class))
                .build();
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO user_roles (user_id, role) VALUES (:id, 'ROLE_HACKER')")
                    .setParameter("id", user.getId())
                    .executeUpdate();
            entityManager.flush();
        }).hasMessageContaining("ck_user_roles_role");
    }
}
