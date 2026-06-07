package wfederico.pneumacare.patient.web.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.web.dto.CreatePatientRequest;
import wfederico.pneumacare.patient.web.dto.PatientIdentifierRequest;

import java.util.List;

/**
 * Validator for the {@link NoDniInList} constraint.
 *
 * <p>On each call to {@link #isValid} this validator queries the
 * {@code patient_identifier_types} catalog for the DNI type id and then
 * checks that none of the {@code additionalIdentifiers} entries carries that type.
 *
 * <p>The catalog is small (6 rows) and this endpoint is not a hot path, so a
 * per-validation DB call is acceptable. Stateless design avoids `@PostConstruct`
 * ordering issues in `@WebMvcTest` slices.
 *
 * <p>If the {@code "DNI"} type is absent from the catalog (e.g. database not yet
 * seeded in a test) the constraint returns {@code true} (fail-open).
 *
 * <h2>Spring integration</h2>
 * Bean Validation uses {@code SpringConstraintValidatorFactory} which enables
 * constructor injection in validators annotated with {@code @Component}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoDniInListValidator
        implements ConstraintValidator<NoDniInList, CreatePatientRequest> {

    private static final String DNI_TYPE_NAME = "DNI";

    private final PatientIdentifierTypeRepository identifierTypeRepository;

    /**
     * Returns {@code false} if any entry in {@code request.additionalIdentifiers()}
     * matches the database ID of the DNI identifier type.
     *
     * @param request the incoming admission request
     * @param context constraint validator context (unused)
     * @return {@code true} when the list contains no DNI entries, {@code false} otherwise
     */
    @Override
    public boolean isValid(CreatePatientRequest request, ConstraintValidatorContext context) {
        List<PatientIdentifierRequest> extras = request.additionalIdentifiers();
        if (extras == null || extras.isEmpty()) {
            return true;
        }

        Integer dniTypeId = identifierTypeRepository.findAll().stream()
                .filter(t -> DNI_TYPE_NAME.equalsIgnoreCase(t.getPatientIdentifierTypeName()))
                .findFirst()
                .map(PatientIdentifierTypeJpaEntity::getPatientIdentifierTypeId)
                .orElse(null);

        if (dniTypeId == null) {
            log.warn("NoDniInListValidator: 'DNI' not found in catalog — constraint is a no-op");
            return true; // catalog not seeded — fail open
        }

        return extras.stream()
                .noneMatch(req -> dniTypeId.equals(req.identifierTypeId()));
    }
}
