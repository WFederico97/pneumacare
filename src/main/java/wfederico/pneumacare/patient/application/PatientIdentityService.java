package wfederico.pneumacare.patient.application;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.patient.web.dto.CreatePatientRequest;
import wfederico.pneumacare.patient.web.dto.PatientIdentifierRequest;
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.constants.ExceptionMessageConstants;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for patient admission.
 *
 * <p>Orchestrates a single atomic transaction that:
 * <ol>
 *   <li>Validates ICU and bed (bed must exist, belong to the ICU, and be AVAILABLE).</li>
 *   <li>Creates the {@code patient_identities} PII record (firstName, lastName,
 *       birthDate) and the {@code patient_identifiers} rows (DNI + any extras).
 *       All PII values are encrypted transparently by the JPA layer.</li>
 *   <li>Creates the {@code patients} operational record linking identity, ICU, and bed.</li>
 *   <li>Marks the bed {@code OCCUPIED}.</li>
 * </ol>
 *
 * <p>If any step fails the entire transaction rolls back, including the bed-status flip.
 *
 * <h2>PII</h2>
 * This service always receives and returns plain-text values.
 * {@link wfederico.pneumacare.shared.security.encryption.AesAttributeConverter}
 * handles AES-256-GCM encrypt/decrypt transparently at the JPA boundary.
 * Never log or expose {@code firstName}, {@code lastName}, {@code dni}, or any
 * identifier value in plain text in log statements.
 *
 * <h2>Concurrency note</h2>
 * Simultaneous admission requests targeting the same bed may both pass the
 * {@code AVAILABLE} check before either flush is committed. This is a known
 * MVP limitation. Hardening would add {@code @Version} on {@link IcuBedJpaEntity}
 * or a {@code SELECT … FOR UPDATE} query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientIdentityService {

    private static final String DNI_TYPE_NAME = "DNI";

    private final PatientIdentityRepository identityRepository;
    private final PatientIdentifierTypeRepository identifierTypeRepository;
    private final PatientRepository patientRepository;
    private final IcuRepository icuRepository;
    private final IcuBedRepository icuBedRepository;

    /**
     * Cached database ID of the DNI identifier type.
     * Loaded once at startup — avoids a per-request catalog query.
     */
    private Integer dniTypeId;

    /**
     * Caches the DNI identifier type id after the bean is fully wired.
     * Logs a warning if the catalog has not been seeded yet
     * (integration tests without seeding will still work: the service
     * falls back to a per-request lookup in that case).
     */
    @PostConstruct
    public void initDniTypeId() {
        identifierTypeRepository.findAll().stream()
                .filter(t -> DNI_TYPE_NAME.equalsIgnoreCase(t.getPatientIdentifierTypeName()))
                .findFirst()
                .ifPresentOrElse(
                        t -> {
                            dniTypeId = t.getPatientIdentifierTypeId();
                            log.debug("PatientIdentityService: cached DNI type id = {}", dniTypeId);
                        },
                        () -> log.warn("PatientIdentityService: 'DNI' not found in catalog at startup")
                );
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Admits a new patient: creates the PII identity record, persists all identifiers
     * (DNI + extras), creates the operational patient row, and marks the bed OCCUPIED.
     *
     * <p>All work is performed in a single transaction. On any failure the entire
     * admission is rolled back.
     *
     * @param request plain-text admission data (validated by {@code @Valid} at the HTTP boundary)
     * @return the admission response with plain-text PII and the operational {@code patientId}
     * @throws BusinessLayerException with {@code 404} if the ICU or bed is not found
     * @throws BusinessLayerException with {@code 400} if the bed is not in the given ICU,
     *         the bed is not AVAILABLE, a duplicate identifier type was supplied,
     *         an unknown identifier type id was supplied, or the identity is already admitted
     */
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        log.debug("Patient admission started: icuId={}, bedId={}", request.icuId(), request.bedId());

        IcuJpaEntity icu = resolveIcu(request.icuId());
        IcuBedJpaEntity bed = resolveAndOccupyBed(request.icuId(), request.bedId());

        PatientIdentityJpaEntity identity = buildAndSaveIdentity(request);

        PatientJpaEntity patient = PatientJpaEntity.builder()
                .icu(icu)
                .identity(identity)
                .bed(bed)
                .clinicalStatus(ClinicalStatus.ADMITTED)
                .build();

        PatientJpaEntity savedPatient = patientRepository.save(patient);

        log.info("Patient admitted: patientId={}, icuId={}, bedId={}, status={}",
                savedPatient.getId(), icu.getId(), bed.getId(), ClinicalStatus.ADMITTED);

        // Re-load with full graph so PatientResponse.from() can navigate all associations
        return patientRepository.findById(savedPatient.getId())
                .map(PatientResponse::from)
                .orElseThrow(() -> new BusinessLayerException(
                        "Failed to reload patient after save", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * Retrieves an admitted patient by their operational UUID, with all
     * associated ICU, bed, identity, and identifier data loaded eagerly.
     *
     * @param id the operational patient UUID ({@code patients.id})
     * @return the response DTO with plain-text PII
     * @throws BusinessLayerException with {@code 404} if no patient exists for {@code id}
     */
    @Transactional(readOnly = true)
    public PatientResponse findById(UUID id) {
        return patientRepository.findById(id)
                .map(PatientResponse::from)
                .orElseThrow(() -> {
                    log.warn("Patient not found: id={}", id);
                    return new BusinessLayerException(
                            "Patient not found with id: " + id,
                            HttpStatus.NOT_FOUND);
                });
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Verifies the ICU exists and returns it.
     *
     * @throws BusinessLayerException 404 if not found
     */
    private IcuJpaEntity resolveIcu(UUID icuId) {
        return icuRepository.findById(icuId)
                .orElseThrow(() -> {
                    log.warn("ICU not found: icuId={}", icuId);
                    return new BusinessLayerException(
                            ExceptionMessageConstants.ICU_NOT_FOUND + icuId,
                            HttpStatus.NOT_FOUND);
                });
    }

    /**
     * Verifies the bed exists within the given ICU, asserts it is AVAILABLE,
     * sets its status to OCCUPIED, and returns the updated entity.
     *
     * <p>Hibernate dirty-checking flushes the status update at commit time —
     * no explicit {@code save()} is needed.
     *
     * @param icuId the ICU UUID from the request
     * @param bedId the bed UUID from the request
     * @return the bed entity with status already set to OCCUPIED
     * @throws BusinessLayerException 400 if bed not found in that ICU
     * @throws BusinessLayerException 400 if bed is not AVAILABLE
     */
    private IcuBedJpaEntity resolveAndOccupyBed(UUID icuId, UUID bedId) {
        IcuBedJpaEntity bed = icuBedRepository.findByIdAndIcu_Id(bedId, icuId)
                .orElseThrow(() -> {
                    log.warn("Bed not found in ICU: bedId={}, icuId={}", bedId, icuId);
                    return new BusinessLayerException(
                            ExceptionMessageConstants.BED_NOT_FOUND,
                            HttpStatus.BAD_REQUEST);
                });

        if (bed.getStatus() != BedStatus.AVAILABLE) {
            log.warn("Bed not available: bedId={}, status={}", bedId, bed.getStatus());
            throw new BusinessLayerException(
                    ExceptionMessageConstants.BED_NOT_AVAILABLE + bed.getStatus().name(),
                    HttpStatus.BAD_REQUEST);
        }

        bed.setStatus(BedStatus.OCCUPIED);
        log.debug("Bed marked OCCUPIED: bedId={}, icuId={}", bedId, icuId);
        return bed;
    }

    /**
     * Builds the {@link PatientIdentityJpaEntity} with all identifiers (DNI + extras)
     * and persists it in a single save.
     *
     * <p>The DNI is converted to a synthetic {@link PatientIdentifierRequest} using
     * the cached {@link #dniTypeId} so it flows through the same validation and
     * encryption pipeline as any other identifier.
     *
     * @param request the incoming admission request
     * @return the saved identity entity (with generated UUID)
     */
    private PatientIdentityJpaEntity buildAndSaveIdentity(CreatePatientRequest request) {
        // Merge DNI (top-level) with any extras into a single list for uniform processing
        List<PatientIdentifierRequest> allIdentifiers = new ArrayList<>();
        allIdentifiers.add(new PatientIdentifierRequest(resolveDniTypeId(), request.dni()));
        if (request.additionalIdentifiers() != null) {
            allIdentifiers.addAll(request.additionalIdentifiers());
        }

        // Guard: reject duplicate identifier types within the same request
        Set<Integer> seenTypeIds = new HashSet<>();
        for (PatientIdentifierRequest req : allIdentifiers) {
            if (!seenTypeIds.add(req.identifierTypeId())) {
                throw new BusinessLayerException(
                        "Duplicate identifier type in request: " + req.identifierTypeId(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Resolve all identifier types in one query (avoids N+1 SELECTs)
        List<Integer> typeIds = allIdentifiers.stream()
                .map(PatientIdentifierRequest::identifierTypeId)
                .toList();

        Map<Integer, PatientIdentifierTypeJpaEntity> typeMap = identifierTypeRepository
                .findAllById(typeIds)
                .stream()
                .collect(Collectors.toMap(
                        PatientIdentifierTypeJpaEntity::getPatientIdentifierTypeId,
                        t -> t));

        List<Integer> unknownTypeIds = typeIds.stream()
                .filter(id -> !typeMap.containsKey(id))
                .toList();
        if (!unknownTypeIds.isEmpty()) {
            throw new BusinessLayerException(
                    "Unknown identifier type IDs: " + unknownTypeIds,
                    HttpStatus.BAD_REQUEST);
        }

        PatientIdentityJpaEntity identity = PatientIdentityJpaEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .build();

        allIdentifiers.forEach(req -> {
            PatientIdentifierJpaEntity identifier = PatientIdentifierJpaEntity.builder()
                    .patientIdentifierName(req.value())
                    .patientIdentity(identity)
                    .patientIdentifierType(typeMap.get(req.identifierTypeId()))
                    .build();
            identity.addIdentifier(identifier);
        });

        return identityRepository.save(identity);
    }

    /**
     * Returns the cached DNI type id, falling back to a live DB lookup if the
     * cache was not populated at startup (e.g. empty catalog in some test contexts).
     *
     * @return the integer primary key of the DNI identifier type
     * @throws BusinessLayerException 400 if the DNI type is not found in the catalog
     */
    private Integer resolveDniTypeId() {
        if (dniTypeId != null) {
            return dniTypeId;
        }
        return identifierTypeRepository.findAll().stream()
                .filter(t -> DNI_TYPE_NAME.equalsIgnoreCase(t.getPatientIdentifierTypeName()))
                .findFirst()
                .map(t -> {
                    dniTypeId = t.getPatientIdentifierTypeId();
                    return dniTypeId;
                })
                .orElseThrow(() -> new BusinessLayerException(
                        "Identifier type 'DNI' not found in catalog",
                        HttpStatus.BAD_REQUEST));
    }
}
