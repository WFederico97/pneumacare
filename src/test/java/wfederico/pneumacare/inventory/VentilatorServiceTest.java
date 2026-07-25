package wfederico.pneumacare.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import wfederico.pneumacare.inventory.application.VentilatorService;
import wfederico.pneumacare.shared.security.CurrentIcuPort;
import wfederico.pneumacare.inventory.domain.VentilatorBrand;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorJpaEntity;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.inventory.infrastructure.persistence.VentilatorModelJpaEntity;
import wfederico.pneumacare.inventory.infrastructure.persistence.VentilatorModelRepository;
import wfederico.pneumacare.inventory.web.dto.CreateVentilatorRequest;
import wfederico.pneumacare.inventory.web.dto.UpdateVentilatorStatusRequest;
import wfederico.pneumacare.inventory.web.dto.VentilatorResponse;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.web.dto.PageResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentilatorServiceTest {

    private static final UUID ICU_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID VENTILATOR_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    @Mock
    private PhysicalVentilatorRepository ventilatorRepository;

    @Mock
    private VentilatorModelRepository modelRepository;

    @Mock
    private CurrentIcuPort currentIcuPort;

    @InjectMocks
    private VentilatorService service;

    private VentilatorModelJpaEntity model;
    private PhysicalVentilatorJpaEntity ventilator;

    @BeforeEach
    void setUp() {
        model = VentilatorModelJpaEntity.builder()
                .id(UUID.randomUUID())
                .brand("TECME")
                .model("GraphNet TS+")
                .build();
        ventilator = PhysicalVentilatorJpaEntity.builder()
                .id(VENTILATOR_ID)
                .icuId(ICU_ID)
                .model(model)
                .serialNumber("SN-001")
                .status(VentilatorStatus.AVAILABLE)
                .build();

        // Every path resolves the session ICU; lenient so tests that fail before
        // reaching it do not trip strict stubbing.
        lenient().when(currentIcuPort.currentIcuId()).thenReturn(ICU_ID);
    }

    private CreateVentilatorRequest validRequest() {
        return new CreateVentilatorRequest("SN-001", VentilatorBrand.TECME, "GraphNet TS+");
    }

    @Test
    @DisplayName("create: persists with AVAILABLE status reusing an existing model row")
    void createReusesExistingModel() {
        when(ventilatorRepository.icuExists(ICU_ID)).thenReturn(true);
        when(ventilatorRepository.existsBySerialNumber("SN-001")).thenReturn(false);
        when(modelRepository.findFirstByBrandAndModel("TECME", "GraphNet TS+"))
                .thenReturn(Optional.of(model));
        when(ventilatorRepository.saveAndFlush(any(PhysicalVentilatorJpaEntity.class)))
                .thenReturn(ventilator);

        VentilatorResponse response = service.create(validRequest());

        assertThat(response.serialNumber()).isEqualTo("SN-001");
        assertThat(response.status()).isEqualTo(VentilatorStatus.AVAILABLE);
        assertThat(response.brand()).isEqualTo("TECME");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: creates the model row when brand+model is unknown")
    void createCreatesModelWhenMissing() {
        when(ventilatorRepository.icuExists(ICU_ID)).thenReturn(true);
        when(ventilatorRepository.existsBySerialNumber("SN-001")).thenReturn(false);
        when(modelRepository.findFirstByBrandAndModel("TECME", "GraphNet TS+"))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any(VentilatorModelJpaEntity.class))).thenReturn(model);
        when(ventilatorRepository.saveAndFlush(any(PhysicalVentilatorJpaEntity.class)))
                .thenReturn(ventilator);

        VentilatorResponse response = service.create(validRequest());

        assertThat(response.modelName()).isEqualTo("GraphNet TS+");
        verify(modelRepository).save(any(VentilatorModelJpaEntity.class));
    }

    @Test
    @DisplayName("create: duplicate serial number is rejected with 409")
    void createRejectsDuplicateSerial() {
        when(ventilatorRepository.icuExists(ICU_ID)).thenReturn(true);
        when(ventilatorRepository.existsBySerialNumber("SN-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(ventilatorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("create: unknown ICU is rejected with 404")
    void createRejectsUnknownIcu() {
        when(ventilatorRepository.icuExists(ICU_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("create: unique-constraint race maps to 409")
    void createMapsConstraintRaceToConflict() {
        when(ventilatorRepository.icuExists(ICU_ID)).thenReturn(true);
        when(ventilatorRepository.existsBySerialNumber("SN-001")).thenReturn(false);
        when(modelRepository.findFirstByBrandAndModel("TECME", "GraphNet TS+"))
                .thenReturn(Optional.of(model));
        when(ventilatorRepository.saveAndFlush(any(PhysicalVentilatorJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uq_physical_ventilators_serial"));

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("list: always scoped to the session ICU, never an unscoped findAll")
    void listIsScopedToSessionIcu() {
        Pageable pageable = PageRequest.of(0, 10);
        when(ventilatorRepository.findByIcuId(ICU_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(ventilator), pageable, 1));

        PageResponse<VentilatorResponse> page = service.list(pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).serialNumber()).isEqualTo("SN-001");
        // An unscoped listing would expose other units' equipment.
        verify(ventilatorRepository, never()).findAll(pageable);
    }

    @Test
    @DisplayName("getById: returns the resource when it exists")
    void getByIdFound() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));

        assertThat(service.getById(VENTILATOR_ID).id()).isEqualTo(VENTILATOR_ID);
    }

    @Test
    @DisplayName("getById: unknown id yields 404")
    void getByIdNotFound() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(VENTILATOR_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("updateStatus: sets the new status and returns the updated resource")
    void updateStatusHappyPath() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        when(ventilatorRepository.saveAndFlush(ventilator)).thenReturn(ventilator);

        VentilatorResponse response =
                service.updateStatus(VENTILATOR_ID, new UpdateVentilatorStatusRequest(VentilatorStatus.MAINTENANCE));

        assertThat(response.status()).isEqualTo(VentilatorStatus.MAINTENANCE);
    }

    @Test
    @DisplayName("updateStatus: unknown id yields 404")
    void updateStatusNotFound() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(
                VENTILATOR_ID, new UpdateVentilatorStatusRequest(VentilatorStatus.MAINTENANCE)))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("delete: removes an unreferenced ventilator")
    void deleteHappyPath() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));

        service.delete(VENTILATOR_ID);

        verify(ventilatorRepository).delete(ventilator);
        verify(ventilatorRepository).flush();
    }

    @Test
    @DisplayName("delete: unknown id yields 404")
    void deleteNotFound() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(VENTILATOR_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("delete: FK violation from clinical history maps to 409")
    void deleteReferencedMapsToConflict() {
        when(ventilatorRepository.findById(VENTILATOR_ID)).thenReturn(Optional.of(ventilator));
        doThrow(new DataIntegrityViolationException("fk_evaluations_ventilator"))
                .when(ventilatorRepository).flush();

        assertThatThrownBy(() -> service.delete(VENTILATOR_ID))
                .isInstanceOf(BusinessLayerException.class)
                .satisfies(ex -> assertThat(((BusinessLayerException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
