package wfederico.pneumacare.clinical.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationRepository;
import wfederico.pneumacare.clinical.web.dto.CreateEvaluationRequest;
import wfederico.pneumacare.clinical.web.dto.EvaluationResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Application service for evaluation persistence.
 *
 * <p>Orchestrates a single atomic transaction that:
 * <ol>
 *   <li>Extracts the authenticated therapist's UUID from the JWT {@code sub} claim.</li>
 *   <li>Calculates RSBI, PaFi, and Cstat using {@link ClinicalMathEngine} directly
 *       (tidal volume is always received in mL per the schema convention).</li>
 *   <li>Builds and saves an immutable {@link EvaluationJpaEntity}.</li>
 *   <li>Returns a fully-populated {@link EvaluationResponse} with computed indices.</li>
 * </ol>
 *
 * <h2>Unit convention</h2>
 * {@code vt} is always in mL as the request DTO and DB schema mandate.
 * RSBI calculation divides by 1000 to convert mL → L before delegating to
 * {@link ClinicalMathEngine#calculateRsbi}.
 *
 * <h2>pplat &gt; peep cross-field constraint</h2>
 * If {@code pplat ≤ peep}, {@link ClinicalMathEngine#calculateCstat} throws
 * {@link IllegalArgumentException}, which this service converts to a
 * {@link BusinessLayerException} with HTTP 400 so the global handler can
 * return a structured error response.
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

    private static final double ML_TO_L = 1000.0;
    private static final int SNAPSHOT_SCALE = 2;
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final EvaluationRepository evaluationRepository;

    /**
     * Persists a new evaluation record with auto-computed clinical index snapshots.
     *
     * <p>Called only when the caller holds {@code ROLE_THERAPIST}
     * (enforced by {@code @PreAuthorize} on the controller method).
     *
     * @param request validated ventilator reading DTO
     * @return the persisted evaluation with calculated RSBI, PaFi, and Cstat snapshots
     * @throws BusinessLayerException with 400 if {@code pplat ≤ peep}
     */
    @Transactional
    public EvaluationResponse create(CreateEvaluationRequest request) {
        UUID createdBy = resolveCreatedBy();

        log.debug("Evaluation creation started: patientId={}, shiftId={}, ventilatorId={}",
                request.patientId(), request.shiftId(), request.physicalVentilatorId());

        double vtMl  = request.vt().doubleValue();
        double pplat = request.pplat().doubleValue();
        double peep  = request.peep().doubleValue();

        double rsbi;
        double pafi;
        double cstat;

        try {
            rsbi  = ClinicalMathEngine.calculateRsbi(request.f().doubleValue(), vtMl / ML_TO_L);
            pafi  = ClinicalMathEngine.calculatePafi(request.pao2().doubleValue(),
                                                     request.fio2().doubleValue());
            cstat = ClinicalMathEngine.calculateCstat(vtMl, pplat, peep);
        } catch (IllegalArgumentException ex) {
            throw new BusinessLayerException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        RsbiInterpretation  rsbiInterpretation  = RsbiInterpretation.from(rsbi);
        PafiClassification  pafiClassification  = PafiClassification.from(pafi);
        CstatInterpretation cstatInterpretation = CstatInterpretation.from(cstat);

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
                .rsbiSnapshot(bd(rsbi))
                .pafiSnapshot(bd(pafi))
                .cstatSnapshot(bd(cstat))
                .createdBy(createdBy)
                .build();

        EvaluationJpaEntity saved = evaluationRepository.save(entity);

        log.info("Evaluation persisted: id={}, patientId={}, rsbi={}, pafi={}, cstat={}",
                saved.getId(), saved.getPatientId(),
                saved.getRsbiSnapshot(), saved.getPafiSnapshot(), saved.getCstatSnapshot());

        return EvaluationResponse.from(saved, rsbiInterpretation, pafiClassification,
                cstatInterpretation);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

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
