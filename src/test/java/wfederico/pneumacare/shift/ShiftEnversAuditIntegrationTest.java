package wfederico.pneumacare.shift;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.audit.ShiftRevisionEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that Envers revision history is captured for {@code medical_shifts}
 * and {@code shift_handovers}, with actor + timestamp.
 *
 * <p>Spins up real PostgreSQL via Testcontainers, dev profile (Hibernate creates
 * the schema including the Envers {@code *_aud} tables; Flyway disabled). Entities
 * are persisted via the repositories (each {@code save} commits in its own
 * transaction, producing an Envers revision) and read back through the
 * {@link AuditReader}.
 *
 * <p>Requires Docker. Run: {@code ./mvnw -Dtest=ShiftEnversAuditIntegrationTest test}
 */
@org.junit.jupiter.api.Disabled("Requires Docker. Run: ./mvnw -Dtest=ShiftEnversAuditIntegrationTest test")
@SpringBootTest(
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ShiftEnversAuditIntegrationTest {

    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000099");

    @Autowired
    private MedicalShiftRepository shiftRepository;
    @Autowired
    private ShiftHandoverRepository handoverRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);

        handoverRepository.deleteAll();
        shiftRepository.deleteAll();

        // Authenticate as a known UUID principal so the revision listener records it.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        ACTOR_ID.toString(), "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("opening then closing a shift records CREATE and UPDATE revisions with a timestamp")
    void shiftCreateAndUpdate_areAudited() {
        MedicalShiftJpaEntity shift = shiftRepository.save(MedicalShiftJpaEntity.builder()
                .icuId(IcuTestDataSeeder.ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build());
        UUID shiftId = shift.getId();

        shift.setStatus(ShiftStatus.CLOSED);
        shift.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
        shiftRepository.save(shift);

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            AuditReader reader = AuditReaderFactory.get(em);

            List<Number> revisions = reader.getRevisions(MedicalShiftJpaEntity.class, shiftId);
            assertThat(revisions).as("one CREATE + one UPDATE revision").hasSize(2);

            MedicalShiftJpaEntity firstRev =
                    reader.find(MedicalShiftJpaEntity.class, shiftId, revisions.get(0));
            MedicalShiftJpaEntity lastRev =
                    reader.find(MedicalShiftJpaEntity.class, shiftId, revisions.get(revisions.size() - 1));
            assertThat(firstRev.getStatus()).isEqualTo(ShiftStatus.OPEN);
            assertThat(lastRev.getStatus()).isEqualTo(ShiftStatus.CLOSED);

            assertThat(reader.getRevisionDate(revisions.get(0)))
                    .as("revision timestamp is recorded").isNotNull();

            ShiftRevisionEntity revInfo =
                    reader.findRevision(ShiftRevisionEntity.class, revisions.get(0));
            assertThat(revInfo.getActorId())
                    .as("revision records the acting user").isEqualTo(ACTOR_ID);
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("creating a handover records a CREATE revision")
    void handoverCreate_isAudited() {
        MedicalShiftJpaEntity shift = shiftRepository.save(MedicalShiftJpaEntity.builder()
                .icuId(IcuTestDataSeeder.ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build());

        ShiftHandoverJpaEntity handover = handoverRepository.save(ShiftHandoverJpaEntity.builder()
                .shiftId(shift.getId())
                .authorId(CHIEF_ID)
                .notesContent("Entrega de guardia")
                .build());

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            AuditReader reader = AuditReaderFactory.get(em);
            List<Number> revisions = reader.getRevisions(ShiftHandoverJpaEntity.class, handover.getId());
            assertThat(revisions).as("one CREATE revision for the handover").hasSize(1);
        } finally {
            em.close();
        }
    }
}
