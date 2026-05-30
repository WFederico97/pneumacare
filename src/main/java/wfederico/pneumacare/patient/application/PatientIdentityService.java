package wfederico.pneumacare.patient.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.patient.web.dto.CreatePatientRequest;
import wfederico.pneumacare.patient.web.dto.PatientIdentifierRequest;
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for patient identity management.
 *
 * <p>PII fields ({@code firstName}, {@code lastName}) and each identifier
 * value are encrypted/decrypted transparently by the JPA layer — this service
 * always receives and returns plain-text values.
 *
 * <p>Structured identifiers (DNI, CUIL, etc.) are created together with the
 * patient identity in a single transaction via {@code CASCADE ALL} on the
 * {@code identifiers} collection.
 */
@Service
@RequiredArgsConstructor
public class PatientIdentityService {

    private final PatientIdentityRepository repository;
    private final PatientIdentifierTypeRepository identifierTypeRepository;

    /**
     * Registers a new patient identity together with all provided identifiers.
     * PII fields and identifier values are encrypted at rest.
     *
     * <p>All identifier types are resolved in a single {@code findAllById} query
     * to avoid N+1 SELECT statements. Any unknown type IDs are reported together
     * in a single error.
     *
     * @param request plain-text patient identity data including at least one identifier
     * @return the created record with plain-text PII (decrypted by JPA on read-back)
     * @throws BusinessLayerException with {@code 400} if the request contains duplicate
     *         identifier type IDs, or if any {@code identifierTypeId} is unknown
     */
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        // Guard: reject duplicate identifier types within the same request
        Set<Integer> seenTypeIds = new HashSet<>();
        for (PatientIdentifierRequest req : request.identifiers()) {
            if (!seenTypeIds.add(req.identifierTypeId())) {
                throw new BusinessLayerException(
                        "Duplicate identifier type in request: " + req.identifierTypeId(),
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Resolve all identifier types in one query (avoids N+1 SELECTs)
        List<Integer> typeIds = request.identifiers().stream()
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

        request.identifiers().forEach(req -> {
            PatientIdentifierJpaEntity identifier = PatientIdentifierJpaEntity.builder()
                    .patientIdentifierName(req.value())
                    .patientIdentity(identity)
                    .patientIdentifierType(typeMap.get(req.identifierTypeId()))
                    .build();

            identity.addIdentifier(identifier);
        });

        return PatientResponse.from(repository.save(identity));
    }

    /**
     * Retrieves a patient identity by its UUID. PII fields and identifier values
     * are decrypted by JPA. Identifiers and their types are fetched in a single
     * query via {@code @EntityGraph} on the repository.
     *
     * @param id the patient identity UUID
     * @return the patient record with plain-text PII and identifiers
     * @throws BusinessLayerException with {@code 404} if no record exists for {@code id}
     */
    @Transactional(readOnly = true)
    public PatientResponse findById(UUID id) {
        return repository.findById(id)
                .map(PatientResponse::from)
                .orElseThrow(() -> new BusinessLayerException(
                        "Patient not found with id: " + id,
                        HttpStatus.NOT_FOUND));
    }
}
