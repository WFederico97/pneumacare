package wfederico.pneumacare.patient.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import wfederico.pneumacare.patient.domain.BedStatus;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.IcuRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientRepository;
import wfederico.pneumacare.patient.web.dto.CreateIcuBedRequest;
import wfederico.pneumacare.patient.web.dto.IcuBedResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service that provides ICU bed data for the dashboard endpoint.
 *
 * <p>The service enforces tenant scoping by reading the authenticated user's
 * {@code icu_id} JWT claim and querying only beds that belong to that ICU.
 *
 * <p>For dashboard rendering, only {@link BedStatus#AVAILABLE} and
 * {@link BedStatus#OCCUPIED} are returned.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IcuBedService {

    private static final List<BedStatus> DASHBOARD_STATUSES = List.of(BedStatus.AVAILABLE, BedStatus.OCCUPIED);

    private final IcuBedRepository icuBedRepository;
    private final IcuRepository icuRepository;
    private final PatientRepository patientRepository;
    private final BedAlertStatusPort bedAlertStatusPort;
    private final Environment environment;

    @Value("${app.security.dev-default-icu-id:cccccccc-0000-0000-0000-000000000001}")
    private String devDefaultIcuId;

    /**
     * Retrieves dashboard-visible beds for the ICU associated with the current token.
     *
     * @return ordered list of bed DTOs for the authenticated ICU; empty when no beds exist
     * @throws BusinessLayerException 401 when the request is unauthenticated outside dev
     * @throws BusinessLayerException 400 when {@code icu_id} claim is missing or invalid
     */
    public List<IcuBedResponse> findBedsForAuthenticatedIcu() {
        UUID icuId = extractIcuIdFromAuthentication();

        List<BedWithPatient> resolved = icuBedRepository
                .findByIcu_IdAndStatusInOrderByBedNumberAsc(icuId, DASHBOARD_STATUSES)
                .stream()
                .map(bed -> {
                    UUID patientId = null;
                    if (bed.getStatus() == BedStatus.OCCUPIED) {
                        patientId = patientRepository
                                .findByBed_IdAndClinicalStatus(bed.getId(), ClinicalStatus.ADMITTED)
                                .map(PatientJpaEntity::getId)
                                .orElse(null);
                    }
                    return new BedWithPatient(bed, patientId);
                })
                .toList();

        Set<UUID> occupyingPatientIds = resolved.stream()
                .map(BedWithPatient::patientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> alertedPatientIds = occupyingPatientIds.isEmpty()
                ? Set.of()
                : bedAlertStatusPort.patientsWithActiveAlert(occupyingPatientIds);

        return resolved.stream()
                .map(bp -> IcuBedResponse.from(
                        bp.bed(),
                        bp.patientId(),
                        bp.patientId() != null && alertedPatientIds.contains(bp.patientId())))
                .toList();
    }

    /** Intermediate pairing of a bed with the UUID of its occupying patient (null when unoccupied). */
    private record BedWithPatient(IcuBedJpaEntity bed, UUID patientId) {
    }

    public IcuBedResponse create(CreateIcuBedRequest request) {
        UUID icuId = extractIcuIdFromAuthentication();

        // Normalize: trim and collapse internal whitespace so "BED  4 " == "BED 4".
        String bedNumber = request.bedNumber().trim().replaceAll("\\s+", " ");
        if (bedNumber.isEmpty()) {
            throw new BusinessLayerException("El número de cama es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if (icuBedRepository.existsByIcu_IdAndBedNumberIgnoreCase(icuId, bedNumber)) {
            throw new BusinessLayerException("Ya existe una cama con ese número", HttpStatus.CONFLICT);
        }

        IcuJpaEntity icu = icuRepository.findById(icuId)
                .orElseThrow(() -> new BusinessLayerException("No se encontró la UCI con id: " + icuId, HttpStatus.NOT_FOUND));

        IcuBedJpaEntity bed = IcuBedJpaEntity.builder()
                .icu(icu)
                .bedNumber(bedNumber)
                .status(BedStatus.AVAILABLE)
                .build();

        return IcuBedResponse.from(icuBedRepository.save(bed));
    }

    /**
     * Extracts and validates the {@code icu_id} claim from the security context JWT principal.
     *
     * @return ICU UUID parsed from the token claim
     * @throws BusinessLayerException 401 when no JWT principal is available outside dev
     * @throws BusinessLayerException 400 when claim is missing or not a valid UUID
     */
    private UUID extractIcuIdFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            if (environment.matchesProfiles("dev")) {
                return parseDevDefaultIcuId();
            }
            throw new BusinessLayerException("No autenticado", HttpStatus.UNAUTHORIZED);
        }

        Object icuIdClaim = jwt.getClaim("icu_id");
        if (icuIdClaim == null) {
            throw new BusinessLayerException("Token inválido: falta claim icu_id", HttpStatus.BAD_REQUEST);
        }

        try {
            return UUID.fromString(icuIdClaim.toString());
        } catch (IllegalArgumentException ex) {
            throw new BusinessLayerException("Token inválido: claim icu_id no es UUID", HttpStatus.BAD_REQUEST);
        }
    }

    private UUID parseDevDefaultIcuId() {
        try {
            return UUID.fromString(devDefaultIcuId);
        } catch (RuntimeException ex) {
            throw new BusinessLayerException("Configuración inválida: app.security.dev-default-icu-id", HttpStatus.BAD_REQUEST);
        }
    }
}
