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
import wfederico.pneumacare.patient.infrastructure.persistence.IcuBedRepository;
import wfederico.pneumacare.patient.web.dto.IcuBedResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.List;
import java.util.UUID;

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

        return icuBedRepository.findByIcu_IdAndStatusInOrderByBedNumberAsc(icuId, DASHBOARD_STATUSES)
                .stream()
                .map(IcuBedResponse::from)
                .toList();
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
