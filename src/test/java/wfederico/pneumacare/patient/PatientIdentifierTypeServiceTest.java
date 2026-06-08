package wfederico.pneumacare.patient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import wfederico.pneumacare.patient.application.PatientIdentifierTypeService;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeJpaEntity;
import wfederico.pneumacare.patient.infrastructure.persistence.PatientIdentifierTypeRepository;
import wfederico.pneumacare.patient.web.dto.IdentifierTypeResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientIdentifierTypeService}.
 *
 * <p>No Spring context loaded. The repository is mocked with Mockito.
 *
 * <h3>Scenarios covered</h3>
 * <ul>
 *   <li>All types returned and mapped to {@link IdentifierTypeResponse}</li>
 *   <li>Results are sorted by {@code patientIdentifierTypeId} ASC (SERIAL order)</li>
 *   <li>Empty repository returns empty list</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatientIdentifierTypeServiceTest {

    @Mock
    private PatientIdentifierTypeRepository repository;

    @InjectMocks
    private PatientIdentifierTypeService service;

    private static final Sort EXPECTED_SORT =
            Sort.by(Sort.Direction.ASC, "patientIdentifierTypeId");

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll — all types are mapped to IdentifierTypeResponse with correct fields")
    void findAll_withSeededTypes_returnsAllMappedCorrectly() {
        // Arrange
        List<PatientIdentifierTypeJpaEntity> entities = List.of(
                buildType(1, "DNI",       "Documento Nacional de Identidad"),
                buildType(2, "CUIL",      "Código Único de Identificación Laboral"),
                buildType(3, "CUIT",      "Código Único de Identificación Tributaria"),
                buildType(4, "LE",        "Libreta de Enrolamiento"),
                buildType(5, "LC",        "Libreta Cívica"),
                buildType(6, "Pasaporte", "Pasaporte"));
        when(repository.findAll(EXPECTED_SORT)).thenReturn(entities);

        // Act
        List<IdentifierTypeResponse> result = service.findAll();

        // Assert
        assertThat(result).hasSize(6);
        assertThat(result.get(0).id()).isEqualTo(1);
        assertThat(result.get(0).name()).isEqualTo("DNI");
        assertThat(result.get(0).description()).isEqualTo("Documento Nacional de Identidad");
        assertThat(result.get(5).id()).isEqualTo(6);
        assertThat(result.get(5).name()).isEqualTo("Pasaporte");
    }

    @Test
    @DisplayName("findAll — repository is queried with ASC sort on patientIdentifierTypeId")
    void findAll_usesSortByPatientIdentifierTypeIdAscending() {
        when(repository.findAll(EXPECTED_SORT)).thenReturn(List.of());

        service.findAll();

        verify(repository).findAll(EXPECTED_SORT);
    }

    @Test
    @DisplayName("findAll — empty catalog returns empty list")
    void findAll_emptyRepository_returnsEmptyList() {
        when(repository.findAll(EXPECTED_SORT)).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private PatientIdentifierTypeJpaEntity buildType(int id, String name, String description) {
        return PatientIdentifierTypeJpaEntity.builder()
                .patientIdentifierTypeId(id)
                .patientIdentifierTypeName(name)
                .patientIdentifierTypeDescription(description)
                .build();
    }
}
