package wfederico.pneumacare.shift;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
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
import wfederico.pneumacare.shift.application.ShiftAuditService;
import wfederico.pneumacare.shift.domain.ShiftStatus;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftRepository;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverRepository;
import wfederico.pneumacare.shift.web.dto.AuditRevisionResponse;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for PNMC-134: the retroactive-edit alert and the audit query API
 * over real persistence: PostgreSQL via Testcontainers, dev profile (Hibernate creates
 * the Envers {@code *_aud} tables; Flyway disabled).
 *
 * <p>Requires Docker.
 */
@org.junit.jupiter.api.Disabled("Requires Docker. Run: ./mvnw -Dtest=ShiftAuditAlertIntegrationTest test")
@SpringBootTest(
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ShiftAuditAlertIntegrationTest {

    private static final String COUNTER = "shift.audit.closed_shift_write_total";
    private static final UUID CHIEF_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000099");

    @Autowired
    private MedicalShiftRepository shiftRepository;
    @Autowired
    private ShiftHandoverRepository handoverRepository;
    @Autowired
    private ShiftAuditService shiftAuditService;
    @Autowired
    private MeterRegistry meterRegistry;

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

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        ACTOR_ID.toString(), "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private double counter(String entityTag) {
        Counter c = meterRegistry.find(COUNTER).tag("entity", entityTag).counter();
        return c == null ? 0d : c.count();
    }

    private MedicalShiftJpaEntity openShift() {
        return shiftRepository.save(MedicalShiftJpaEntity.builder()
                .icuId(IcuTestDataSeeder.ICU_ID)
                .chiefUserId(CHIEF_ID)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.OPEN)
                .build());
    }

    private MedicalShiftJpaEntity close(MedicalShiftJpaEntity shift) {
        shift.setStatus(ShiftStatus.CLOSED);
        shift.setEndTime(OffsetDateTime.now(ZoneOffset.UTC));
        return shiftRepository.save(shift);
    }

    @Test
    @DisplayName("editing a CLOSED shift increments the alert counter and is not blocked")
    void retroactiveShiftEdit_alertsButPersists() {
        MedicalShiftJpaEntity shift = close(openShift());
        double before = counter("medical_shift");

        // Retroactive edit: the shift is already CLOSED.
        shift.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        MedicalShiftJpaEntity persisted = shiftRepository.save(shift);

        assertThat(counter("medical_shift"))
                .as("retroactive close-shift edit raises one alert").isEqualTo(before + 1);
        // The write was recorded, not vetoed.
        assertThat(shiftRepository.findById(persisted.getId()))
                .get()
                .extracting(MedicalShiftJpaEntity::getEndTime)
                .isNotNull();
    }

    @Test
    @DisplayName("the legitimate OPEN -> CLOSED close transition raises no alert")
    void legitimateClose_doesNotAlert() {
        double before = counter("medical_shift");

        close(openShift());

        assertThat(counter("medical_shift")).isEqualTo(before);
    }

    @Test
    @DisplayName("inserting a handover under a CLOSED shift increments the alert counter")
    void handoverUnderClosedShift_alerts() {
        MedicalShiftJpaEntity shift = close(openShift());
        double before = counter("handover");

        handoverRepository.save(ShiftHandoverJpaEntity.builder()
                .shiftId(shift.getId())
                .authorId(CHIEF_ID)
                .notesContent("Anomalous note on a closed shift")
                .build());

        assertThat(counter("handover")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("shift audit history returns CREATE then UPDATE revisions with the actor")
    void auditHistory_returnsRevisionsWithActor() {
        MedicalShiftJpaEntity shift = close(openShift());

        List<AuditRevisionResponse<ShiftResponse>> history =
                shiftAuditService.getShiftHistory(shift.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).revisionType()).isEqualTo("CREATE");
        assertThat(history.get(1).revisionType()).isEqualTo("UPDATE");
        assertThat(history.get(0).actorId()).isEqualTo(ACTOR_ID);
        assertThat(history.get(1).entity().status()).isEqualTo(ShiftStatus.CLOSED);
    }
}
