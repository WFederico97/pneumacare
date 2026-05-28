package wfederico.pneumacare.patient.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.web.dto.IdentifierTypeResponse;

import java.util.List;

/**
 * Application service for the patient identifier type catalog.
 *
 * <p>Identifier types (DNI, CUIL, CUIT, Passport, etc.) are catalog data —
 * not PII. They are seeded once via Flyway (staging/prod) or
 * {@link wfederico.pneumacare.patient.infrastructure.IdentifierTypeDataSeeder} (dev),
 * and exposed read-only through the API for frontend dropdowns.
 */
@Service
@RequiredArgsConstructor
public class PatientIdentifierTypeService {

    private final PatientIdentifierTypeRepository repository;

    /**
     * Returns all identifier types, ordered by insertion order (SERIAL id).
     * This preserves the seed order: DNI first, then CUIL, CUIT, LE, LC, Pasaporte.
     *
     * @return immutable list of identifier type responses
     */
    @Transactional(readOnly = true)
    public List<IdentifierTypeResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "patientIdentifierTypeId"))
                .stream()
                .map(IdentifierTypeResponse::from)
                .toList();
    }
}
