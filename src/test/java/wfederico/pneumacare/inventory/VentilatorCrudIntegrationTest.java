package wfederico.pneumacare.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.inventory.application.VentilatorService;
import wfederico.pneumacare.inventory.domain.VentilatorBrand;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.web.dto.CreateVentilatorRequest;
import wfederico.pneumacare.inventory.web.dto.UpdateVentilatorStatusRequest;
import wfederico.pneumacare.inventory.web.dto.VentilatorResponse;
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack CRUD round trip against Testcontainers Postgres, using the ICU
 * seeded by {@link IcuTestDataSeeder} in the dev profile.
 *
 * <p>Requires Docker. Run: {@code ./mvnw -Dtest=VentilatorCrudIntegrationTest test}
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=VentilatorCrudIntegrationTest test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class VentilatorCrudIntegrationTest {

    @Autowired
    private VentilatorService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L);
    }

    @BeforeEach
    void cleanOwnState() {
        // Shared cached context/DB across integration tests: remove only the
        // rows this test creates (serials prefixed IT-SN-).
        jdbcTemplate.update("DELETE FROM physical_ventilators WHERE serial_number LIKE 'IT-SN-%'");
    }

    @Test
    @DisplayName("create, read, update status and delete a ventilator end to end")
    void fullCrudRoundTrip() {
        VentilatorResponse created = service.create(new CreateVentilatorRequest(
                "IT-SN-001", VentilatorBrand.TECME, "GraphNet TS+", IcuTestDataSeeder.ICU_ID));
        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.status()).isEqualTo(VentilatorStatus.AVAILABLE);

        assertThat(service.getById(created.id()).serialNumber()).isEqualTo("IT-SN-001");

        VentilatorResponse updated = service.updateStatus(
                created.id(), new UpdateVentilatorStatusRequest(VentilatorStatus.MAINTENANCE));
        assertThat(updated.status()).isEqualTo(VentilatorStatus.MAINTENANCE);

        service.delete(created.id());
        assertThatThrownBy(() -> service.getById(created.id()))
                .isInstanceOf(BusinessLayerException.class);
    }

    @Test
    @DisplayName("duplicate serial number is rejected against the real unique constraint")
    void duplicateSerialAgainstRealConstraint() {
        service.create(new CreateVentilatorRequest(
                "IT-SN-002", VentilatorBrand.NEUMOVENT, "GraphNet Neo", IcuTestDataSeeder.ICU_ID));

        assertThatThrownBy(() -> service.create(new CreateVentilatorRequest(
                "IT-SN-002", VentilatorBrand.NEUMOVENT, "GraphNet Neo", IcuTestDataSeeder.ICU_ID)))
                .isInstanceOf(BusinessLayerException.class);
    }
}
