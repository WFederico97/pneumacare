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
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentityRepository;
import wfederico.pneumacare.patient.web.dto.CreatePatientRequest;
import wfederico.pneumacare.patient.web.dto.PatientIdentifierRequest;
import wfederico.pneumacare.patient.web.dto.PatientResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientIdentityService}.
 *
 * <p>No Spring context is loaded. The two repositories are mocked with Mockito,
 * and the JPA {@link wfederico.pneumacare.shared.security.encryption.AesAttributeConverter}
 * does NOT run — entities hold plain-text values, which is correct for isolated
 * service-layer tests.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>Standard DNI admission (AC: standard identifier creation)</li>
 *   <li>Foreign patient admission with Passport identifier</li>
 *   <li>Patient with multiple identifiers (DNI + CUIL)</li>
 *   <li>Duplicate identifier type rejected before any DB call</li>
 *   <li>Unknown identifier type ID rejected with 400</li>
 *   <li>findById — found and not-found paths</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatientIdentityServiceTest {

    @Mock
    private PatientIdentityRepository repository;

    @Mock
    private PatientIdentifierTypeRepository identifierTypeRepository;

    @InjectMocks
    private PatientIdentityService service;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static final String FIRST_NAME = "Juan";
    private static final String LAST_NAME  = "Pérez";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 20);

    private PatientIdentifierTypeJpaEntity dniType;
    private PatientIdentifierTypeJpaEntity cuilType;
    private PatientIdentifierTypeJpaEntity passportType;

    @BeforeEach
    void setUp() {
        dniType      = buildType(1, "DNI",       "Documento Nacional de Identidad");
        cuilType     = buildType(2, "CUIL",      "Código Único de Identificación Laboral");
        passportType = buildType(6, "Pasaporte", "Pasaporte");
    }

    // -------------------------------------------------------------------------
    // create() — acceptance-criteria scenarios
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Standard DNI admission — patient created with single DNI identifier")
    void create_standardDniAdmission_returnsPatientWithDni() {
        // Arrange
        CreatePatientRequest request = request(new PatientIdentifierRequest(1, "35123456"));
        when(identifierTypeRepository.findById(1)).thenReturn(Optional.of(dniType));

        UUID id = UUID.randomUUID();
        when(repository.save(any(PatientIdentityJpaEntity.class)))
                .thenReturn(buildEntity(id, List.of(buildIdentifier("35123456", dniType))));

        // Act
        PatientResponse response = service.create(request);

        // Assert
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.firstName()).isEqualTo(FIRST_NAME);
        assertThat(response.lastName()).isEqualTo(LAST_NAME);
        assertThat(response.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(response.identifiers()).hasSize(1);
        assertThat(response.identifiers().get(0).typeName()).isEqualTo("DNI");
        assertThat(response.identifiers().get(0).value()).isEqualTo("35123456");

        verify(repository).save(any(PatientIdentityJpaEntity.class));
    }

    @Test
    @DisplayName("Foreign patient admission — patient created with Pasaporte identifier")
    void create_foreignPatientAdmission_returnsPatientWithPassport() {
        // Arrange
        CreatePatientRequest request = request(new PatientIdentifierRequest(6, "AB123456"));
        when(identifierTypeRepository.findById(6)).thenReturn(Optional.of(passportType));

        UUID id = UUID.randomUUID();
        when(repository.save(any(PatientIdentityJpaEntity.class)))
                .thenReturn(buildEntity(id, List.of(buildIdentifier("AB123456", passportType))));

        // Act
        PatientResponse response = service.create(request);

        // Assert
        assertThat(response.identifiers()).hasSize(1);
        assertThat(response.identifiers().get(0).typeName()).isEqualTo("Pasaporte");
        assertThat(response.identifiers().get(0).value()).isEqualTo("AB123456");

        verify(repository).save(any(PatientIdentityJpaEntity.class));
    }

    @Test
    @DisplayName("Multiple identifiers — DNI and CUIL both persisted and returned")
    void create_multipleIdentifiers_dniAndCuil_allSaved() {
        // Arrange
        CreatePatientRequest request = new CreatePatientRequest(
                FIRST_NAME, LAST_NAME, BIRTH_DATE,
                List.of(
                        new PatientIdentifierRequest(1, "35123456"),
                        new PatientIdentifierRequest(2, "20351234568")));

        when(identifierTypeRepository.findById(1)).thenReturn(Optional.of(dniType));
        when(identifierTypeRepository.findById(2)).thenReturn(Optional.of(cuilType));

        UUID id = UUID.randomUUID();
        when(repository.save(any(PatientIdentityJpaEntity.class)))
                .thenReturn(buildEntity(id, List.of(
                        buildIdentifier("35123456", dniType),
                        buildIdentifier("20351234568", cuilType))));

        // Act
        PatientResponse response = service.create(request);

        // Assert
        assertThat(response.identifiers()).hasSize(2);
        assertThat(response.identifiers())
                .extracting(i -> i.typeName())
                .containsExactly("DNI", "CUIL");

        verify(repository).save(any(PatientIdentityJpaEntity.class));
    }

    // -------------------------------------------------------------------------
    // create() — duplicate identifier type prevention
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Duplicate identifier type prevention — same typeId twice in request throws 400")
    void create_duplicateIdentifierType_throwsBadRequest() {
        // Two DNI entries in the same registration request
        CreatePatientRequest request = new CreatePatientRequest(
                FIRST_NAME, LAST_NAME, BIRTH_DATE,
                List.of(
                        new PatientIdentifierRequest(1, "35123456"),
                        new PatientIdentifierRequest(1, "99999999")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Duplicate identifier type prevention — no DB call is made when duplicate detected")
    void create_duplicateIdentifierType_doesNotCallAnyRepository() {
        CreatePatientRequest request = new CreatePatientRequest(
                FIRST_NAME, LAST_NAME, BIRTH_DATE,
                List.of(
                        new PatientIdentifierRequest(1, "35123456"),
                        new PatientIdentifierRequest(1, "99999999")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class);

        // Duplicate check must fail fast — no repository must be touched
        verifyNoInteractions(identifierTypeRepository);
        verifyNoInteractions(repository);
    }

    // -------------------------------------------------------------------------
    // create() — unknown identifier type
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unknown identifier type ID — throws 400 Bad Request")
    void create_unknownIdentifierTypeId_throwsBadRequest() {
        CreatePatientRequest request = request(new PatientIdentifierRequest(99999, "35123456"));
        when(identifierTypeRepository.findById(99999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById — existing patient returns correct response with identifiers")
    void findById_existingPatient_returnsPatientWithIdentifiers() {
        UUID id = UUID.randomUUID();
        PatientIdentityJpaEntity entity = buildEntity(id,
                List.of(buildIdentifier("35123456", dniType)));
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        PatientResponse response = service.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.firstName()).isEqualTo(FIRST_NAME);
        assertThat(response.lastName()).isEqualTo(LAST_NAME);
        assertThat(response.identifiers()).hasSize(1);
        assertThat(response.identifiers().get(0).typeName()).isEqualTo("DNI");
        assertThat(response.identifiers().get(0).value()).isEqualTo("35123456");
    }

    @Test
    @DisplayName("findById — non-existent UUID throws 404 Not Found")
    void findById_nonExistentPatient_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(BusinessLayerException.class)
                .extracting(e -> ((BusinessLayerException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a single-identifier request for the default patient. */
    private CreatePatientRequest request(PatientIdentifierRequest... identifiers) {
        return new CreatePatientRequest(FIRST_NAME, LAST_NAME, BIRTH_DATE, List.of(identifiers));
    }

    private PatientIdentityJpaEntity buildEntity(UUID id,
                                                  List<PatientIdentifierJpaEntity> identifiers) {
        return PatientIdentityJpaEntity.builder()
                .id(id)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .birthDate(BIRTH_DATE)
                .identifiers(new ArrayList<>(identifiers))
                .build();
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
