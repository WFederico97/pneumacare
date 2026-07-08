package wfederico.pneumacare.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.inventory.application.AssetAssignmentService;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.AssetAssignmentJpaEntity;
import wfederico.pneumacare.inventory.infrastructure.persistence.AssetAssignmentRepository;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorJpaEntity;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.inventory.web.dto.ActiveAssignmentResponse;
import wfederico.pneumacare.inventory.web.dto.AssetAssignmentResponse;
import wfederico.pneumacare.inventory.web.dto.AssignAssetRequest;
import wfederico.pneumacare.inventory.web.dto.UnassignAssetRequest;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAssignmentServiceTest {

    private static final UUID VENTILATOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000009");

    @Mock
    private AssetAssignmentRepository assignmentRepository;
    @Mock
    private PhysicalVentilatorRepository ventilatorRepository;

    @InjectMocks
    private AssetAssignmentService service;

    private PhysicalVentilatorJpaEntity ventilator;

    @BeforeEach
    void setUp() {
        ventilator = PhysicalVentilatorJpaEntity.builder()
                .id(VENTILATOR_ID)
                .icuId(UUID.randomUUID())
                .serialNumber("SN-001")
                .status(VentilatorStatus.AVAILABLE)
                .build();
    }

    private AssignAssetRequest assignRequest() {
        return new AssignAssetRequest(VENTILATOR_ID, PATIENT_ID);
    }

    private AssetAssignmentJpaEntity savedAssignment(OffsetDateTime releasedAt) {
        return AssetAssignmentJpaEntity.builder()
                .id(ASSIGNMENT_ID)
                .ventilatorId(VENTILATOR_ID)
                .patientId(PATIENT_ID)
                .assignedAt(OffsetDateTime.now())
                .releasedAt(releasedAt)
                .build();
    }

    @Test
    @DisplayName("assign: available ventilator + valid patient links and sets IN_USE")
    void assignHappyPath() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.patientExists(PATIENT_ID)).thenReturn(true);
        when(assignmentRepository.existsByPatientIdAndReleasedAtIsNull(PATIENT_ID)).thenReturn(false);
        when(assignmentRepository.saveAndFlush(any(AssetAssignmentJpaEntity.class)))
                .thenReturn(savedAssignment(null));

        AssetAssignmentResponse response = service.assign(assignRequest());

        assertThat(response.status()).isEqualTo(VentilatorStatus.IN_USE);
        assertThat(response.ventilatorId()).isEqualTo(VENTILATOR_ID);
        assertThat(response.patientId()).isEqualTo(PATIENT_ID);
        assertThat(ventilator.getStatus()).isEqualTo(VentilatorStatus.IN_USE);
        verify(ventilatorRepository).save(ventilator);
    }

    @Test
    @DisplayName("assign: ventilator in MAINTENANCE is rejected with 400")
    void assignBlockedByMaintenance() {
        ventilator.setStatus(VentilatorStatus.MAINTENANCE);
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.patientExists(PATIENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(assignRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(assignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("assign: ventilator already IN_USE is rejected with 400")
    void assignBlockedByInUse() {
        ventilator.setStatus(VentilatorStatus.IN_USE);
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.patientExists(PATIENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(assignRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("assign: unknown ventilator yields 404")
    void assignUnknownVentilator() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(assignRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("assign: unknown patient yields 404")
    void assignUnknownPatient() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.patientExists(PATIENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(assignRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("assign: patient already has an active assignment yields 409")
    void assignPatientAlreadyAssigned() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.patientExists(PATIENT_ID)).thenReturn(true);
        when(assignmentRepository.existsByPatientIdAndReleasedAtIsNull(PATIENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(assignRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("assign: unique-index race maps to 409")
    void assignRaceMapsToConflict() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.patientExists(PATIENT_ID)).thenReturn(true);
        when(assignmentRepository.existsByPatientIdAndReleasedAtIsNull(PATIENT_ID)).thenReturn(false);
        when(assignmentRepository.saveAndFlush(any(AssetAssignmentJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_asset_assignments_active_ventilator"));

        assertThatThrownBy(() -> service.assign(assignRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("unassign: active assignment is released and ventilator set AVAILABLE")
    void unassignHappyPath() {
        ventilator.setStatus(VentilatorStatus.IN_USE);
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.findByVentilatorIdAndReleasedAtIsNull(VENTILATOR_ID))
                .thenReturn(Optional.of(savedAssignment(null)));
        when(assignmentRepository.save(any(AssetAssignmentJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AssetAssignmentResponse response = service.unassign(new UnassignAssetRequest(VENTILATOR_ID));

        assertThat(response.status()).isEqualTo(VentilatorStatus.AVAILABLE);
        assertThat(response.releasedAt()).isNotNull();
        assertThat(ventilator.getStatus()).isEqualTo(VentilatorStatus.AVAILABLE);
        verify(ventilatorRepository).save(ventilator);
    }

    @Test
    @DisplayName("unassign: no active assignment yields 409")
    void unassignNoActiveAssignment() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(assignmentRepository.findByVentilatorIdAndReleasedAtIsNull(VENTILATOR_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(new UnassignAssetRequest(VENTILATOR_ID)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("unassign: unknown ventilator yields 404")
    void unassignUnknownVentilator() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(new UnassignAssetRequest(VENTILATOR_ID)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("findActiveForPatient: returns the active assignment with the ventilator serial")
    void findActiveForPatientReturnsAssignment() {
        when(assignmentRepository.findByPatientIdAndReleasedAtIsNull(PATIENT_ID))
                .thenReturn(Optional.of(savedAssignment(null)));
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));

        ActiveAssignmentResponse response = service.findActiveForPatient(PATIENT_ID);

        assertThat(response).isNotNull();
        assertThat(response.ventilatorId()).isEqualTo(VENTILATOR_ID);
        assertThat(response.serialNumber()).isEqualTo("SN-001");
    }

    @Test
    @DisplayName("findActiveForPatient: returns null when the patient has no active assignment")
    void findActiveForPatientNoneReturnsNull() {
        when(assignmentRepository.findByPatientIdAndReleasedAtIsNull(PATIENT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.findActiveForPatient(PATIENT_ID)).isNull();
    }
}
