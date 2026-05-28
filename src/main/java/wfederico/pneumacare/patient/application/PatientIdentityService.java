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
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.UUID;

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
     * @param request plain-text patient identity data including at least one identifier
     * @return the created record with plain-text PII (decrypted by JPA on read-back)
     * @throws BusinessLayerException with {@code 400} if any {@code identifierTypeId} is unknown
     */
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        PatientIdentityJpaEntity identity = PatientIdentityJpaEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .build();

        request.identifiers().forEach(req -> {
            PatientIdentifierTypeJpaEntity type = identifierTypeRepository
                    .findById(req.identifierTypeId())
                    .orElseThrow(() -> new BusinessLayerException(
                            "Identifier type not found: " + req.identifierTypeId(),
                            HttpStatus.BAD_REQUEST));

            PatientIdentifierJpaEntity identifier = PatientIdentifierJpaEntity.builder()
                    .patientIdentifierName(req.value())
                    .patientIdentity(identity)
                    .patientIdentifierType(type)
                    .build();

            identity.getIdentifiers().add(identifier);
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
