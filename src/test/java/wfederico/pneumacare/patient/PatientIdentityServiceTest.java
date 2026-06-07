package wfederico.pneumacare.patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.patient.application.PatientIdentityService;
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
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientIdentityService} — the patient admission orchestrator.
 *
 * <p>No Spring context is loaded. All five repositories are mocked with Mockito.
 * The JPA {@link wfederico.pneumacare.shared.security.encryption.AesAttributeConverter}
 * does NOT run — entities hold plain-text values, which is correct for isolated
 * service-layer tests.
 *
 * <p>{@code @PostConstruct} is not invoked by {@code @InjectMocks}, so
 * {@code initDniTypeId()} is called manually in {@link #setUp()} after stubbing
 * {@code identifierTypeRepository.findAll()} to return the DNI type.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>Happy path — full admission with DNI only</li>
 *   <li>Happy path — admission with DNI + CUIL additional identifier</li>
 *   <li>Bed is occupied after admission (status mutation verified)</li>
 *   <li>ICU not found throws 404</li>
 *   <li>Bed not found in ICU throws 400</li>
 *   <li>Bed is OCCUPIED throws 400</li>
 *   <li>Bed is MAINTENANCE throws 400</li>
 *   <li>Unknown additional identifier type ID throws 400</li>
 *   <li>Duplicate identifier type in additionalIdentifiers throws 400</li>
 *   <li>findById — found path returns full response</li>
 *   <li>findById — not-found throws 404</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatientIdentityServiceTest {

    @Mock private PatientIdentityRepository identityRepository;
    @Mock private PatientIdentifierTypeRepository identifierTypeRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private IcuRepository icuRepository;
    @Mock private IcuBedRepository icuBedRepository;

    @InjectMocks
    private PatientIdentityService service;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final String     FIRST_NAME = "Juan";
    private static final String     LAST_NAME  = "Pérez";
    private static final String     DNI_VALUE  = "35123456";
    private static final LocalDate  BIRTH_DATE = LocalDate.of(1990, 5, 20);

    private static final UUID ICU_ID    = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID BED_ID    = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID IDENTITY_ID = UUID.randomUUID();

    private PatientIdentifierTypeJpaEntity dniType;
    private PatientIdentifierTypeJpaEntity cuilType;
    private IcuJpaEntity                   icu;
    private IcuBedJpaEntity                availableBed;

    @BeforeEach
    void setUp() {
        dniType  = buildType(1, "DNI",  "Documento Nacional de Identidad");
        cuilType = buildType(2, "CUIL", "Código Único de Identificación Laboral");

        icu = IcuJpaEntity.builder()
                .id(ICU_ID)
                .hospitalId(UUID.randomUUID())
                .name("UTI Central")
                .code("UTI-01")
                .build();

        availableBed = IcuBedJpaEntity.builder()
                .id(BED_ID)
                .icu(icu)
                .bedNumber("BED-001")
                .status(BedStatus.AVAILABLE)
                .build();

        // Simulate @PostConstruct — resolves and caches dniTypeId.
        // Use lenient() so Mockito strict-stubs don't flag the findAll() call as
        // "unnecessary" in tests that throw before reaching resolveDniTypeId().
        org.mockito.Mockito.lenient()
                .when(identifierTypeRepository.findAll())
                .thenReturn(List.of(dniType, cuilType));
        service.initDniTypeId();
    }

    // ── create() — happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("create — DNI-only admission — patient admitted, 201 response with patientId and bedId")
    void create_dniOnlyAdmission_returnsResponseWithPatientAndBedId() {
        // Arrange
        CreatePatientRequest request = minimalRequest();
        stubIcuAndBed();
        PatientIdentityJpaEntity savedIdentity = buildIdentityEntity(IDENTITY_ID,
                List.of(buildIdentifier(DNI_VALUE, dniType)));
        when(identifierTypeRepository.findAllById(List.of(1))).thenReturn(List.of(dniType));
        when(identityRepository.save(any())).thenReturn(savedIdentity);

        PatientJpaEntity savedPatient = buildPatientEntity(PATIENT_ID, icu, availableBed, savedIdentity);
        when(patientRepository.save(any())).thenReturn(savedPatient);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(savedPatient));

        // Act
        PatientResponse response = service.create(request);

        // Assert
        assertThat(response.patientId()).isEqualTo(PATIENT_ID);
        assertThat(response.firstName()).isEqualTo(FIRST_NAME);
        assertThat(response.lastName()).isEqualTo(LAST_NAME);
        assertThat(response.dni()).isEqualTo(DNI_VALUE);
        assertThat(response.icuId()).isEqualTo(ICU_ID);
        assertThat(response.bedId()).isEqualTo(BED_ID);
        assertThat(response.clinicalStatus()).isEqualTo("ADMITTED");
        assertThat(response.additionalIdentifiers()).isEmpty();

        verify(identityRepository).save(any(PatientIdentityJpaEntity.class));
        verify(patientRepository).save(any(PatientJpaEntity.class));
    }

    @Test
    @DisplayName("create — DNI + CUIL admission — both identifiers persisted, CUIL in additionalIdentifiers")
    void create_dniAndCuilAdmission_bothIdentifiersInResponse() {
        // Arrange
        CreatePatientRequest request = requestWithAdditional(new PatientIdentifierRequest(2, "20351234568"));
        stubIcuAndBed();
        PatientIdentityJpaEntity savedIdentity = buildIdentityEntity(IDENTITY_ID, List.of(
                buildIdentifier(DNI_VALUE, dniType),
                buildIdentifier("20351234568", cuilType)));
        when(identifierTypeRepository.findAllById(List.of(1, 2))).thenReturn(List.of(dniType, cuilType));
        when(identityRepository.save(any())).thenReturn(savedIdentity);

        PatientJpaEntity savedPatient = buildPatientEntity(PATIENT_ID, icu, availableBed, savedIdentity);
        when(patientRepository.save(any())).thenReturn(savedPatient);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(savedPatient));

        // Act
        PatientResponse response = service.create(request);

        // Assert
        assertThat(response.dni()).isEqualTo(DNI_VALUE);
        assertThat(response.additionalIdentifiers()).hasSize(1);
        assertThat(response.additionalIdentifiers().get(0).typeName()).isEqualTo("CUIL");
        assertThat(response.additionalIdentifiers().get(0).value()).isEqualTo("20351234568");
    }

    @Test
    @DisplayName("create — bed status is OCCUPIED after successful admission")
    void create_successfulAdmission_bedStatusIsOccupied() {
        // Arrange
        stubIcuAndBed();
        PatientIdentityJpaEntity savedIdentity = buildIdentityEntity(IDENTITY_ID,
                List.of(buildIdentifier(DNI_VALUE, dniType)));
        when(identifierTypeRepository.findAllById(any())).thenReturn(List.of(dniType));
        when(identityRepository.save(any())).thenReturn(savedIdentity);

        PatientJpaEntity savedPatient = buildPatientEntity(PATIENT_ID, icu, availableBed, savedIdentity);
        when(patientRepository.save(any())).thenReturn(savedPatient);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(savedPatient));

        // Act
        service.create(minimalRequest());

        // Assert — dirty-check: the same IcuBedJpaEntity instance was mutated to OCCUPIED
        assertThat(availableBed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
    }

    // ── create() — ICU / bed guard failures ──────────────────────────────────

    @Test
    @DisplayName("create — ICU not found — throws 404 Not Found")
    void create_icuNotFound_throwsNotFoundException() {
        when(icuRepository.findById(ICU_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(minimalRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("create — bed not found in ICU — throws 400 Bad Request")
    void create_bedNotInIcu_throwsBadRequest() {
        when(icuRepository.findById(ICU_ID)).thenReturn(Optional.of(icu));
        when(icuBedRepository.findByIdAndIcu_Id(BED_ID, ICU_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(minimalRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("create — bed is OCCUPIED — throws 400 Bad Request")
    void create_bedAlreadyOccupied_throwsBadRequest() {
        IcuBedJpaEntity occupiedBed = IcuBedJpaEntity.builder()
                .id(BED_ID).icu(icu).bedNumber("BED-001").status(BedStatus.OCCUPIED).build();
        when(icuRepository.findById(ICU_ID)).thenReturn(Optional.of(icu));
        when(icuBedRepository.findByIdAndIcu_Id(BED_ID, ICU_ID)).thenReturn(Optional.of(occupiedBed));

        assertThatThrownBy(() -> service.create(minimalRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("create — bed in MAINTENANCE — throws 400 Bad Request")
    void create_bedInMaintenance_throwsBadRequest() {
        IcuBedJpaEntity maintenanceBed = IcuBedJpaEntity.builder()
                .id(BED_ID).icu(icu).bedNumber("BED-003").status(BedStatus.MAINTENANCE).build();
        when(icuRepository.findById(ICU_ID)).thenReturn(Optional.of(icu));
        when(icuBedRepository.findByIdAndIcu_Id(BED_ID, ICU_ID)).thenReturn(Optional.of(maintenanceBed));

        assertThatThrownBy(() -> service.create(minimalRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── create() — identifier guard failures ─────────────────────────────────

    @Test
    @DisplayName("create — unknown additional identifier type ID — throws 400 Bad Request")
    void create_unknownAdditionalIdentifierTypeId_throwsBadRequest() {
        stubIcuAndBed();
        when(identifierTypeRepository.findAllById(List.of(1, 99999))).thenReturn(List.of(dniType));

        CreatePatientRequest request = requestWithAdditional(new PatientIdentifierRequest(99999, "???"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("create — duplicate identifier type in additionalIdentifiers — throws 400 Bad Request")
    void create_duplicateAdditionalIdentifierType_throwsBadRequest() {
        stubIcuAndBed();
        // Two CUIL entries submitted
        CreatePatientRequest request = new CreatePatientRequest(
                FIRST_NAME, LAST_NAME, BIRTH_DATE, DNI_VALUE, ICU_ID, BED_ID,
                List.of(
                        new PatientIdentifierRequest(2, "20351234568"),
                        new PatientIdentifierRequest(2, "20999999999")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── findById() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById — existing patient — returns response with patientId, bedId, and DNI")
    void findById_existingPatient_returnsFullResponse() {
        PatientIdentityJpaEntity identity = buildIdentityEntity(IDENTITY_ID,
                List.of(buildIdentifier(DNI_VALUE, dniType)));
        PatientJpaEntity patient = buildPatientEntity(PATIENT_ID, icu, availableBed, identity);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        PatientResponse response = service.findById(PATIENT_ID);

        assertThat(response.patientId()).isEqualTo(PATIENT_ID);
        assertThat(response.dni()).isEqualTo(DNI_VALUE);
        assertThat(response.bedId()).isEqualTo(BED_ID);
        assertThat(response.icuId()).isEqualTo(ICU_ID);
    }

    @Test
    @DisplayName("findById — non-existent UUID — throws 404 Not Found")
    void findById_nonExistentPatient_throwsNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(patientRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknownId))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubIcuAndBed() {
        when(icuRepository.findById(ICU_ID)).thenReturn(Optional.of(icu));
        when(icuBedRepository.findByIdAndIcu_Id(BED_ID, ICU_ID)).thenReturn(Optional.of(availableBed));
    }

    private CreatePatientRequest minimalRequest() {
        return new CreatePatientRequest(
                FIRST_NAME, LAST_NAME, BIRTH_DATE, DNI_VALUE, ICU_ID, BED_ID, null);
    }

    private CreatePatientRequest requestWithAdditional(PatientIdentifierRequest... extras) {
        return new CreatePatientRequest(
                FIRST_NAME, LAST_NAME, BIRTH_DATE, DNI_VALUE, ICU_ID, BED_ID, List.of(extras));
    }

    private PatientIdentityJpaEntity buildIdentityEntity(UUID id,
            List<PatientIdentifierJpaEntity> identifiers) {
        return PatientIdentityJpaEntity.builder()
                .id(id)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .birthDate(BIRTH_DATE)
                .identifiers(new ArrayList<>(identifiers))
                .build();
    }

    private PatientJpaEntity buildPatientEntity(UUID id, IcuJpaEntity icuEntity,
            IcuBedJpaEntity bed, PatientIdentityJpaEntity identity) {
        PatientJpaEntity patient = PatientJpaEntity.builder()
                .id(id)
                .icu(icuEntity)
                .bed(bed)
                .identity(identity)
                .clinicalStatus(ClinicalStatus.ADMITTED)
                .admissionDate(OffsetDateTime.now())
                .build();
        return patient;
    }

    private PatientIdentifierJpaEntity buildIdentifier(String value,
            PatientIdentifierTypeJpaEntity type) {
        return PatientIdentifierJpaEntity.builder()
                .patientIdentifierName(value)
                .patientIdentifierType(type)
                .build();
    }

    private PatientIdentifierTypeJpaEntity buildType(int id, String name, String description) {
        return PatientIdentifierTypeJpaEntity.builder()
                .patientIdentifierTypeId(id)
                .patientIdentifierTypeName(name)
                .patientIdentifierTypeDescription(description)
                .build();
    }
}
