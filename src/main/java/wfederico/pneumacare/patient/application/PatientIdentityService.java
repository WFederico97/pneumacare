package wfederico.pneumacare.patient.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.patient.web.dto.CreatePatientRequest;
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.util.UUID;

/**
 * Application service for patient identity management.
 *
 * <p>PII fields ({@code firstName}, {@code lastName}, {@code nationalId}) are
 * encrypted/decrypted transparently by the JPA layer — this service always
 * receives and returns plain-text values.
 */
@Service
@RequiredArgsConstructor
public class PatientIdentityService {

    private final PatientIdentityRepository repository;

    /**
     * Registers a new patient identity. PII fields are encrypted at rest.
     *
     * @param request plain-text patient identity data
     * @return the created record with plain-text PII (decrypted by JPA on read-back)
     */
    @Transactional
    public PatientResponse create(CreatePatientRequest request) {
        PatientIdentityJpaEntity entity = PatientIdentityJpaEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .nationalId(request.nationalId())
                .birthDate(request.birthDate())
                .build();

        return PatientResponse.from(repository.save(entity));
    }

    /**
     * Retrieves a patient identity by its UUID. PII fields are decrypted by JPA.
     *
     * @param id the patient identity UUID
     * @return the patient record with plain-text PII
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
