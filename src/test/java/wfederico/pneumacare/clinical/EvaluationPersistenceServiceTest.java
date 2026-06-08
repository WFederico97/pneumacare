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
import wfederico.pneumacare.clinical.application.strategy.VentilatorFactory;
import wfederico.pneumacare.clinical.application.strategy.VentilatorStrategy;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.domain.VentilatorBrand;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.CstatResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.PafiResult;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult.RsbiResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EvaluationPersistenceService}.
 *
 * <p>No Spring context is loaded. {@link EvaluationRepository},
 * {@link VentilatorFactory}, and {@link VentilatorStrategy} are all mocked so
 * the test focuses on the service's persistence and routing responsibilities,
 * not on the math (which is covered by {@code TecmeStrategyTest},
 * {@code NeumoventStrategyTest}, and {@code ClinicalMathEngineTest}).
 *
 * <p>The security context is set up manually in {@code @BeforeEach} and cleared
 * in {@code @AfterEach} — {@code @PreAuthorize} AOP does not fire in pure unit
 * tests (it is exercised at the controller layer via
 * {@link EvaluationControllerTest}).
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>Happy path — RSBI/PaFi/Cstat snapshots from the strategy result are stored</li>
 *   <li>Happy path — created_by is extracted from the security context principal</li>
 *   <li>Brand routing — request.brand() is forwarded to {@link VentilatorFactory#resolve}</li>
 *   <li>Strategy receives a canonical {@link VentilatorReading} built from the request</li>
 *   <li>No JWT (anonymous principal) — created_by falls back to nil UUID</li>
 *   <li>Strategy throws IllegalArgumentException → service wraps as
 *       {@link BusinessLayerException} with HTTP 400</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationPersistenceServiceTest {

    private static final UUID PATIENT_ID    = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID SHIFT_ID      = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID VENTILATOR_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID THERAPIST_ID  = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID NIL_UUID      = new UUID(0L, 0L);

    /** Mocked strategy result — the service must persist these exact values, rounded to 2dp. */
    private static final VentilatorEvaluationResult MOCK_RESULT = new VentilatorEvaluationResult(
            new RsbiResult(30.0,  RsbiInterpretation.FAVORABLE),
            new PafiResult(212.5, PafiClassification.MILD_ARDS),
            new CstatResult(25.0, CstatInterpretation.LOW));

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private VentilatorFactory ventilatorFactory;

    @Mock
    private VentilatorStrategy strategy;

    @InjectMocks
    private EvaluationPersistenceService service;

    /** Standard valid request: brand=TECME, f=15, vt=500 mL, pao2=85, fio2=0.4, pplat=25, peep=5. */
    private CreateEvaluationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateEvaluationRequest(
                PATIENT_ID,
                SHIFT_ID,
                VENTILATOR_ID,
                VentilatorBrand.TECME,
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("0.40"),
                new BigDecimal("25"),
                new BigDecimal("5"),
                null);

        // Factory routes any brand to the mock strategy by default; specific tests override
        // when they care about the brand argument.
        lenient().when(ventilatorFactory.resolve(any(VentilatorBrand.class))).thenReturn(strategy);
        lenient().when(strategy.evaluate(any(VentilatorReading.class))).thenReturn(MOCK_RESULT);

        // Repository echoes the entity it receives, populating only id + timestamp.
        lenient().when(evaluationRepository.save(any())).thenAnswer(inv -> {
            EvaluationJpaEntity arg = inv.getArgument(0);
            return EvaluationJpaEntity.builder()
                    .id(UUID.randomUUID())
                    .patientId(arg.getPatientId())
                    .shiftId(arg.getShiftId())
                    .physicalVentilatorId(arg.getPhysicalVentilatorId())
                    .evaluationTime(OffsetDateTime.now())
                    .f(arg.getF())
                    .vt(arg.getVt())
                    .pao2(arg.getPao2())
                    .fio2(arg.getFio2())
                    .pplat(arg.getPplat())
                    .peep(arg.getPeep())
                    .extendedParameters(arg.getExtendedParameters())
                    .rsbiSnapshot(arg.getRsbiSnapshot())
                    .pafiSnapshot(arg.getPafiSnapshot())
                    .cstatSnapshot(arg.getCstatSnapshot())
                    .createdBy(arg.getCreatedBy())
                    .build();
        });

        setTherapistAuth();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Snapshot persistence ─────────────────────────────────────────────

    @Test
    @DisplayName("create_validRequest_rsbiSnapshotComesFromStrategyResultRoundedToScale2")
    void create_validRequest_rsbiSnapshotIsCorrect() {
        EvaluationResponse response = service.create(validRequest);

        assertThat(response.rsbiSnapshot())
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("create_validRequest_pafiSnapshotComesFromStrategyResultRoundedToScale2")
    void create_validRequest_pafiSnapshotIsCorrect() {
        EvaluationResponse response = service.create(validRequest);

        assertThat(response.pafiSnapshot())
                .isEqualByComparingTo(new BigDecimal("212.50"));
    }

    @Test
    @DisplayName("create_validRequest_cstatSnapshotComesFromStrategyResultRoundedToScale2")
    void create_validRequest_cstatSnapshotIsCorrect() {
        EvaluationResponse response = service.create(validRequest);

        assertThat(response.cstatSnapshot())
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    // ── Interpretation propagation ───────────────────────────────────────

    @Test
    @DisplayName("create_validRequest_interpretationsFromStrategyResultPropagatedToResponse")
    void create_validRequest_interpretationsPropagated() {
        EvaluationResponse response = service.create(validRequest);

        assertThat(response.rsbiInterpretation()).isEqualTo(RsbiInterpretation.FAVORABLE);
        assertThat(response.pafiClassification()).isEqualTo(PafiClassification.MILD_ARDS);
        assertThat(response.cstatInterpretation()).isEqualTo(CstatInterpretation.LOW);
    }

    // ── Brand routing ────────────────────────────────────────────────────

    @Test
    @DisplayName("create_validRequest_resolvesStrategyByBrandFromRequest")
    void create_validRequest_factoryResolvedByBrand() {
        service.create(validRequest);

        verify(ventilatorFactory).resolve(eq(VentilatorBrand.TECME));
    }

    @Test
    @DisplayName("create_neumoventBrand_resolvesNeumoventStrategy")
    void create_neumoventBrand_factoryResolvedByBrand() {
        CreateEvaluationRequest neumoventRequest = new CreateEvaluationRequest(
                PATIENT_ID, SHIFT_ID, VENTILATOR_ID,
                VentilatorBrand.NEUMOVENT,
                new BigDecimal("15"),
                new BigDecimal("500"),
                new BigDecimal("85"),
                new BigDecimal("0.40"),
                new BigDecimal("25"),
                new BigDecimal("5"),
                null);

        service.create(neumoventRequest);

        verify(ventilatorFactory).resolve(eq(VentilatorBrand.NEUMOVENT));
    }

    // ── Reading construction ─────────────────────────────────────────────

    @Test
    @DisplayName("create_validRequest_strategyReceivesCanonicalReadingFromRequestPrimitives")
    void create_validRequest_strategyReceivesCanonicalReading() {
        service.create(validRequest);

        ArgumentCaptor<VentilatorReading> captor = ArgumentCaptor.forClass(VentilatorReading.class);
        verify(strategy).evaluate(captor.capture());

        VentilatorReading actual = captor.getValue();
        assertThat(actual.respiratoryRate()).isEqualTo(15.0);
        assertThat(actual.tidalVolume()).isEqualTo(500.0);
        assertThat(actual.pao2()).isEqualTo(85.0);
        assertThat(actual.fio2()).isEqualTo(0.40);
        assertThat(actual.plateauPressure()).isEqualTo(25.0);
        assertThat(actual.peepTotal()).isEqualTo(5.0);
    }

    // ── created_by from security context ─────────────────────────────────

    @Test
    @DisplayName("create_validRequest_createdByPopulatedFromSecurityContext")
    void create_validRequest_createdByFromContext() {
        service.create(validRequest);

        ArgumentCaptor<EvaluationJpaEntity> captor =
                ArgumentCaptor.forClass(EvaluationJpaEntity.class);
        verify(evaluationRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(THERAPIST_ID);
    }

    @Test
    @DisplayName("create_anonymousPrincipal_createdByFallsBackToNilUuid")
    void create_anonymousPrincipal_usesNilUuid() {
        SecurityContextHolder.clearContext();

        service.create(validRequest);

        ArgumentCaptor<EvaluationJpaEntity> captor =
                ArgumentCaptor.forClass(EvaluationJpaEntity.class);
        verify(evaluationRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedBy()).isEqualTo(NIL_UUID);
    }

    // ── Strategy errors → BusinessLayerException(400) ────────────────────

    @Test
    @DisplayName("create_strategyRejectsInputs_throwsBusinessLayerException400")
    void create_strategyThrowsIllegalArgument_wrappedAsBusinessException() {
        when(strategy.evaluate(any(VentilatorReading.class)))
                .thenThrow(new IllegalArgumentException(
                        "La presión meseta debe ser mayor que el PEEP total"));

        assertThatThrownBy(() -> service.create(validRequest))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(
                        ((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void setTherapistAuth() {
        var auth = new UsernamePasswordAuthenticationToken(
                THERAPIST_ID.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_THERAPIST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
