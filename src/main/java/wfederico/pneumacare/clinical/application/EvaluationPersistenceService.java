package wfederico.pneumacare.clinical.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.clinical.application.strategy.VentilatorFactory;
import wfederico.pneumacare.clinical.application.strategy.VentilatorStrategy;
import wfederico.pneumacare.clinical.domain.MetricBreach;
import wfederico.pneumacare.clinical.domain.RiskThresholdEvaluator;
import wfederico.pneumacare.clinical.domain.input.VentilatorReading;
import wfederico.pneumacare.clinical.domain.output.VentilatorEvaluationResult;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.clinical.web.dto.CreateEvaluationRequest;
import wfederico.pneumacare.clinical.web.dto.EvaluationResponse;
import wfederico.pneumacare.shared.event.PatientRiskEvent;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Application service for evaluation persistence.
 *
 * <p>Orchestrates a single atomic transaction that:
 * <ol>
 *   <li>Extracts the authenticated therapist's UUID from the JWT {@code sub} claim.</li>
 *   <li>Resolves the brand-specific {@link VentilatorStrategy} via
 *       {@link VentilatorFactory#resolve}.</li>
 *   <li>Builds a canonical {@link VentilatorReading} (tidal volume always in mL)
 *       and delegates index calculation to the strategy.</li>
 *   <li>Builds and saves an immutable {@link EvaluationJpaEntity}.</li>
 *   <li>Returns a fully-populated {@link EvaluationResponse} with computed indices
 *       and their clinical interpretations.</li>
 * </ol>
 *
 * <h2>Brand routing</h2>
 * The {@code brand} field of the request is the discriminator. The strategy
 * receives a canonical reading with {@code tidalVolume} in mL — every brand
 * adapter is responsible for any unit conversions its formulae require.
 *
 * <h2>pplat &gt; peep cross-field constraint</h2>
 * The DTO enforces {@code pplat &gt; peep} via
 * {@link CreateEvaluationRequest#isPplatGreaterThanPeep()} so most violations
 * never reach this service. As a defence-in-depth measure, the math engine
 * (called by the strategy) also throws {@link IllegalArgumentException} on
 * the same condition; this service translates that to a
 * {@link BusinessLayerException} carrying HTTP 400.
 *
 * <h2>created_by</h2>
 * Extracted from {@link SecurityContextHolder}. In staging/prod the JWT {@code sub}
 * claim is a UUID string. In dev (no JWT), {@code auth.getName()} returns
 * {@code "anonymousUser"}; the service falls back to a nil UUID so the record can
 * still be saved during local development.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationPersistenceService {

    private static final int SNAPSHOT_SCALE = 2;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final EvaluationRepository evaluationRepository;
    private final VentilatorFactory ventilatorFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final PatientBedLabelPort patientBedLabelPort;

    /**
     * Persists a new evaluation record with auto-computed clinical index snapshots.
     *
     * <p>Index calculation is delegated to the brand-specific
     * {@link VentilatorStrategy} resolved from {@code request.brand()}.
     *
     * @param request validated ventilator reading DTO
     * @return the persisted evaluation with calculated RSBI, PaFi, and Cstat snapshots
     * @throws BusinessLayerException with 400 if the strategy rejects the inputs
     *         (e.g. {@code pplat ≤ peep})
     */
    @Transactional
    public EvaluationResponse create(CreateEvaluationRequest request) {
        UUID createdBy = resolveCreatedBy();

        log.debug("Evaluation creation started: patientId={}, shiftId={}, ventilatorId={}, brand={}",
                request.patientId(), request.shiftId(),
                request.physicalVentilatorId(), request.brand());

        VentilatorReading reading = new VentilatorReading(
                request.f().doubleValue(),
                request.vt().doubleValue(),
                request.pao2().doubleValue(),
                request.fio2().doubleValue(),
                request.pplat().doubleValue(),
                request.peep().doubleValue());

        VentilatorEvaluationResult result;
        try {
            result = ventilatorFactory.resolve(request.brand()).evaluate(reading);
        } catch (IllegalArgumentException ex) {
            throw new BusinessLayerException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        List<MetricBreach> breaches = RiskThresholdEvaluator.evaluate(
                result.rsbi().value(),
                result.pafi().value(),
                result.cstat().value());

        EvaluationJpaEntity entity = EvaluationJpaEntity.builder()
                .patientId(request.patientId())
                .shiftId(request.shiftId())
                .physicalVentilatorId(request.physicalVentilatorId())
                .f(request.f())
                .vt(request.vt())
                .pao2(request.pao2())
                .fio2(request.fio2())
                .pplat(request.pplat())
                .peep(request.peep())
                .extendedParameters(request.extendedParameters())
                .rsbiSnapshot(bd(result.rsbi().value()))
                .pafiSnapshot(bd(result.pafi().value()))
                .cstatSnapshot(bd(result.cstat().value()))
                .rsbiInterpretation(result.rsbi().interpretation())
                .pafiClassification(result.pafi().classification())
                .cstatInterpretation(result.cstat().interpretation())
                .alertTriggered(!breaches.isEmpty())
                .createdBy(createdBy)
                .build();

        EvaluationJpaEntity saved = evaluationRepository.save(entity);

        log.info("Evaluation persisted: id={}, patientId={}, brand={}, rsbi={}, pafi={}, cstat={}",
                saved.getId(), saved.getPatientId(), request.brand(),
                saved.getRsbiSnapshot(), saved.getPafiSnapshot(), saved.getCstatSnapshot());

        if (!breaches.isEmpty()) {
            publishRiskEvent(saved, breaches);
        }

        return EvaluationResponse.from(saved);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds and publishes a {@link PatientRiskEvent} for a persisted evaluation
     * that breached one or more risk thresholds. Bed label is resolved via the
     * {@link PatientBedLabelPort}; a patient with no assigned bed yields a null
     * {@code bedLabel}.
     */
    private void publishRiskEvent(EvaluationJpaEntity saved, List<MetricBreach> breaches) {
        String bedLabel = patientBedLabelPort.findBedLabel(saved.getPatientId()).orElse(null);

        List<PatientRiskEvent.BreachedMetric> metrics = breaches.stream()
                .map(b -> new PatientRiskEvent.BreachedMetric(b.metric().name(), b.value()))
                .toList();

        PatientRiskEvent event = new PatientRiskEvent(
                saved.getPatientId(), saved.getShiftId(), bedLabel, metrics);

        eventPublisher.publishEvent(event);

        log.info("PatientRiskEvent published: patientId={}, shiftId={}, breachedMetrics={}",
                saved.getPatientId(), saved.getShiftId(), metrics.size());
    }

    /**
     * Resolves the authenticated user's UUID from the security context.
     *
     * <p>In staging/prod the JWT {@code sub} claim is a UUID string, obtained via
     * {@link Authentication#getName()}. In dev (no JWT), {@code getName()} returns
     * {@code "anonymousUser"} which cannot be parsed as a UUID; the method falls
     * back to a nil UUID so local development is not blocked.
     *
     * @return the therapist's UUID, or a nil UUID in dev when no JWT is present
     */
    private UUID resolveCreatedBy() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return NIL_UUID;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            log.debug("Evaluation: principal '{}' is not a UUID — using nil UUID (dev profile).",
                    auth.getName());
            return NIL_UUID;
        }
    }

    /** Rounds a double to {@value #SNAPSHOT_SCALE} decimal places as BigDecimal. */
    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(SNAPSHOT_SCALE, RoundingMode.HALF_UP);
    }
}
