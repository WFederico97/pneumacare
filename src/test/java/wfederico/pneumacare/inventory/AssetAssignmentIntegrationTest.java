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
import wfederico.pneumacare.inventory.application.AssetAssignmentService;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.inventory.web.dto.AssetAssignmentResponse;
import wfederico.pneumacare.inventory.web.dto.AssignAssetRequest;
import wfederico.pneumacare.inventory.web.dto.UnassignAssetRequest;
import wfederico.pneumacare.patient.infrastructure.IcuTestDataSeeder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack assign/unassign round trip against Testcontainers Postgres.
 * Disabled by repo convention; run individually with:
 * mvnw.cmd test -Dtest=AssetAssignmentIntegrationTest
 */
@Disabled("Requires Docker. Run: ./mvnw -Dtest=AssetAssignmentIntegrationTest test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class AssetAssignmentIntegrationTest {

    private static final UUID MODEL_ID = UUID.fromString("11111111-0000-0000-0000-0000000000a1");
    private static final UUID VENTILATOR_ID = UUID.fromString("11111111-0000-0000-0000-0000000000a2");
    private static final UUID IDENTITY_ID = UUID.fromString("11111111-0000-0000-0000-0000000000a3");
    private static final UUID PATIENT_ID = UUID.fromString("11111111-0000-0000-0000-0000000000a4");

    @Autowired
    private AssetAssignmentService service;
    @Autowired
    private PhysicalVentilatorRepository ventilatorRepository;
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
    void seedGraph() {
        // FK-safe cleanup of this test's rows, then reseed a ventilator + patient
        // in the ICU that IcuTestDataSeeder provides under dev.
        jdbcTemplate.update("DELETE FROM asset_assignments WHERE ventilator_id = ?", VENTILATOR_ID);
        jdbcTemplate.update("DELETE FROM physical_ventilators WHERE id = ?", VENTILATOR_ID);
        jdbcTemplate.update("DELETE FROM ventilator_models WHERE id = ?", MODEL_ID);
        jdbcTemplate.update("DELETE FROM patients WHERE id = ?", PATIENT_ID);
        jdbcTemplate.update("DELETE FROM patient_identities WHERE id = ?", IDENTITY_ID);

        jdbcTemplate.update("INSERT INTO patient_identities (id, first_name, last_name, national_id, birth_date) "
                + "VALUES (?, 'IT', 'IT', 'IT-DNI-103', DATE '2000-01-01')", IDENTITY_ID);
        jdbcTemplate.update("INSERT INTO patients (id, icu_id, identity_id) VALUES (?, ?, ?)",
                PATIENT_ID, IcuTestDataSeeder.ICU_ID, IDENTITY_ID);
        jdbcTemplate.update("INSERT INTO ventilator_models (id, brand, model) VALUES (?, 'TECME', 'IT-Model')",
                MODEL_ID);
        jdbcTemplate.update("INSERT INTO physical_ventilators (id, icu_id, model_id, serial_number, status) "
                + "VALUES (?, ?, ?, 'IT-SN-103', 'AVAILABLE')", VENTILATOR_ID, IcuTestDataSeeder.ICU_ID, MODEL_ID);
    }

    @Test
    @DisplayName("assign then unassign round-trips the ventilator status")
    void assignThenUnassign() {
        AssetAssignmentResponse assigned = service.assign(new AssignAssetRequest(VENTILATOR_ID, PATIENT_ID));
        assertThat(assigned.status()).isEqualTo(VentilatorStatus.IN_USE);
        assertThat(assigned.releasedAt()).isNull();
        assertThat(ventilatorRepository.findById(VENTILATOR_ID).orElseThrow().getStatus())
                .isEqualTo(VentilatorStatus.IN_USE);

        AssetAssignmentResponse released = service.unassign(new UnassignAssetRequest(VENTILATOR_ID));
        assertThat(released.status()).isEqualTo(VentilatorStatus.AVAILABLE);
        assertThat(released.releasedAt()).isNotNull();
        assertThat(ventilatorRepository.findById(VENTILATOR_ID).orElseThrow().getStatus())
                .isEqualTo(VentilatorStatus.AVAILABLE);
    }
}
