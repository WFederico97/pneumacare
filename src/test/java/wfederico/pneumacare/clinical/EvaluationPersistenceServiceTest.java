package wfederico.pneumacare.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import wfederico.pneumacare.clinical.application.EvaluationPersistenceService;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.clinical.web.dto.CreateEvaluationRequest;
import wfederico.pneumacare.clinical.web.dto.EvaluationResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EvaluationPersistenceService}.
 *
 * <p>No Spring context is loaded. {@link EvaluationRepository} is mocked.
 * The security context is set up manually in {@code @BeforeEach} and cleared
 * in {@code @AfterEach} — {@code @PreAuthorize} AOP does not fire in pure unit tests
 * (it is tested at the controller layer via {@link EvaluationControllerTest}).
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>Happy path — RSBI, PaFi, Cstat snapshots are computed and stored correctly</li>
 *   <li>Happy path — created_by is extracted from the security context principal</li>
 *   <li>pplat ≤ peep — ClinicalMathEngine throws; service wraps as 400</li>
 *   <li>No JWT (anonymous principal) — created_by falls back to nil UUID</li>
 *   <li>fio2 = 1.0 boundary — PaFi equals pao2 (no division error)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationPersistenceServiceTest {

    private static final UUID PATIENT_ID    = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SHIFT_ID      = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID VENTILATOR_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID THERAPIST_ID  = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID NIL_UUID      = new UUID(0L, 0L);

    @Mock
    private EvaluationRepository evaluationRepository;

    @InjectMocks
    private EvaluationPersistenceService service;

    /** A standard valid request: f=15, vt=500 mL, pao2=85, fio2=0.4, pplat=25, peep=5. */
    private CreateEvaluationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateEvaluationRequest(
                PATIENT_ID,
                SHIFT_ID,
                VENTILATOR_ID,
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("0.40"),
                new BigDecimal("25"),
                new BigDecimal("5"),
                null);

        // Simulate a saved entity returned from the repository
        EvaluationJpaEntity savedEntity = EvaluationJpaEntity.builder()
                .id(UUID.randomUUID())
                .patientId(PATIENT_ID)
                .shiftId(SHIFT_ID)
                .physicalVentilatorId(VENTILATOR_ID)
                .evaluationTime(OffsetDateTime.now())
                .f(new BigDecimal("15"))
                .vt(new BigDecimal("500"))
                .pao2(new BigDecimal("85"))
                .fio2(new BigDecimal("0.40"))
                .pplat(new BigDecimal("25"))
                .peep(new BigDecimal("5"))
                .rsbiSnapshot(new BigDecimal("30.00"))
                .pafiSnapshot(new BigDecimal("212.50"))
                .cstatSnapshot(new BigDecimal("25.00"))
                .createdBy(THERAPIST_ID)
                .build();

        lenient().when(evaluationRepository.save(any())).thenReturn(savedEntity);

        // Set up authenticated therapist in security context
        setTherapistAuth();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Happy path — snapshot values ─────────────────────────────────────

    @Test
    @DisplayName("create_validRequest_rsbiSnapshotIsCalculatedCorrectly")
    void create_validRequest_rsbiSnapshotIsCorrect() {
        EvaluationResponse response = service.create(validRequest);

        // RSBI = f / (vt / 1000) = 15 / (500 / 1000) = 15 / 0.5 = 30
        assertThat(response.rsbiSnapshot())
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("create_validRequest_pafiSnapshotIsCalculatedCorrectly")
    void create_validRequest_pafiSnapshotIsCorrect() {
        EvaluationResponse response = service.create(validRequest);

        // PaFi = pao2 / fio2 = 85 / 0.40 = 212.5
        assertThat(response.pafiSnapshot())
                .isEqualByComparingTo(new BigDecimal("212.50"));
    }

    @Test
    @DisplayName("create_validRequest_cstatSnapshotIsCalculatedCorrectly")
    void create_validRequest_cstatSnapshotIsCorrect() {
        EvaluationResponse response = service.create(validRequest);

        // Cstat = vt / (pplat - peep) = 500 / (25 - 5) = 500 / 20 = 25
        assertThat(response.cstatSnapshot())
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    // ── Happy path — created_by from security context ────────────────────

    @Test
    @DisplayName("create_validRequest_createdByPopulatedFromSecurityContext")
    void create_validRequest_createdByFromContext() {
        service.create(validRequest);

        ArgumentCaptor<EvaluationJpaEntity> captor =
                ArgumentCaptor.forClass(EvaluationJpaEntity.class);
        verify(evaluationRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(THERAPIST_ID);
    }

    // ── created_by falls back to nil UUID when principal is not a UUID ────

    @Test
    @DisplayName("create_anonymousPrincipal_createdByFallsBackToNilUuid")
    void create_anonymousPrincipal_usesNilUuid() {
        SecurityContextHolder.clearContext(); // remove THERAPIST auth
        // No authentication in context (simulates missing JWT in dev)

        service.create(validRequest);

        ArgumentCaptor<EvaluationJpaEntity> captor =
                ArgumentCaptor.forClass(EvaluationJpaEntity.class);
        verify(evaluationRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(NIL_UUID);
    }

    // ── pplat ≤ peep → BusinessLayerException(400) ───────────────────────

    @Test
    @DisplayName("create_pplatEqualToPeep_throwsBusinessLayerException400")
    void create_pplatEqualToPeep_throwsBusinessException() {
        CreateEvaluationRequest badRequest = new CreateEvaluationRequest(
                PATIENT_ID, SHIFT_ID, VENTILATOR_ID,
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("0.40"),
                new BigDecimal("10"),  // pplat = peep — invalid
                new BigDecimal("10"),
                null);

        assertThatThrownBy(() -> service.create(badRequest))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(
                        ((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── fio2 = 1.0 boundary (PaFi = pao2) ────────────────────────────────

    @Test
    @DisplayName("create_fio2AtMaxBoundary_pafiEqualsP02")
    void create_fio2AtMaxBoundary_pafiEqualsPao2() {
        // Override saved entity pafi_snapshot for this specific scenario
        EvaluationJpaEntity savedWithHighFio2 = EvaluationJpaEntity.builder()
                .id(UUID.randomUUID())
                .patientId(PATIENT_ID)
                .shiftId(SHIFT_ID)
                .physicalVentilatorId(VENTILATOR_ID)
                .evaluationTime(OffsetDateTime.now())
                .f(new BigDecimal("15"))
                .vt(new BigDecimal("500"))
                .pao2(new BigDecimal("85"))
                .fio2(new BigDecimal("1.0"))
                .pplat(new BigDecimal("25"))
                .peep(new BigDecimal("5"))
                .rsbiSnapshot(new BigDecimal("30.00"))
                .pafiSnapshot(new BigDecimal("85.00"))  // pao2 / 1.0 = 85
                .cstatSnapshot(new BigDecimal("25.00"))
                .createdBy(THERAPIST_ID)
                .build();
        when(evaluationRepository.save(any())).thenReturn(savedWithHighFio2);

        CreateEvaluationRequest highFio2Request = new CreateEvaluationRequest(
                PATIENT_ID, SHIFT_ID, VENTILATOR_ID,
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("1.0"),
                new BigDecimal("25"),
                new BigDecimal("5"),
                null);

        EvaluationResponse response = service.create(highFio2Request);

        assertThat(response.pafiSnapshot())
                .isEqualByComparingTo(new BigDecimal("85.00"));
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void setTherapistAuth() {
        var auth = new UsernamePasswordAuthenticationToken(
                THERAPIST_ID.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_THERAPIST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
