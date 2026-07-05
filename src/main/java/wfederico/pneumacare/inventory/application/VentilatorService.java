package wfederico.pneumacare.inventory.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.UUID;

/**
 * Application service for physical ventilator inventory management.
 *
 * <p>The API contract is flat (serial + brand + model name) while the schema
 * is normalized: creation resolves-or-creates the {@code ventilator_models}
 * row for the requested (brand, model) pair.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VentilatorService {

    private final PhysicalVentilatorRepository ventilatorRepository;
    private final VentilatorModelRepository modelRepository;

    @Transactional
    public VentilatorResponse create(CreateVentilatorRequest request) {
        if (!ventilatorRepository.icuExists(request.icuId())) {
            throw new BusinessLayerException(
                    "No se encontró la UCI con id: " + request.icuId(), HttpStatus.NOT_FOUND);
        }

        String serialNumber = request.serialNumber().trim();
        if (ventilatorRepository.existsBySerialNumber(serialNumber)) {
            throw new BusinessLayerException(
                    "Ya existe un ventilador con ese número de serie", HttpStatus.CONFLICT);
        }

        VentilatorModelJpaEntity model = modelRepository
                .findFirstByBrandAndModel(request.brand().name(), request.modelName())
                .orElseGet(() -> modelRepository.save(VentilatorModelJpaEntity.builder()
                        .brand(request.brand().name())
                        .model(request.modelName())
                        .build()));

        PhysicalVentilatorJpaEntity ventilator = PhysicalVentilatorJpaEntity.builder()
                .icuId(request.icuId())
                .model(model)
                .serialNumber(serialNumber)
                .status(VentilatorStatus.AVAILABLE)
                .build();

        try {
            // saveAndFlush so audit timestamps are populated in the 201 body
            // and the unique constraint fires inside this try block.
            return VentilatorResponse.from(ventilatorRepository.saveAndFlush(ventilator));
        } catch (DataIntegrityViolationException ex) {
            // Race with a concurrent registration of the same serial.
            throw new BusinessLayerException(
                    "Ya existe un ventilador con ese número de serie", HttpStatus.CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<VentilatorResponse> list(Pageable pageable, UUID icuId) {
        Page<PhysicalVentilatorJpaEntity> page = (icuId == null)
                ? ventilatorRepository.findAll(pageable)
                : ventilatorRepository.findByIcuId(icuId, pageable);
        return PageResponse.from(page, VentilatorResponse::from);
    }

    @Transactional(readOnly = true)
    public VentilatorResponse getById(UUID id) {
        return VentilatorResponse.from(findOrThrow(id));
    }

    @Transactional
    public VentilatorResponse updateStatus(UUID id, UpdateVentilatorStatusRequest request) {
        PhysicalVentilatorJpaEntity ventilator = findOrThrow(id);
        ventilator.setStatus(request.status());
        return VentilatorResponse.from(ventilatorRepository.saveAndFlush(ventilator));
    }

    @Transactional
    public void delete(UUID id) {
        PhysicalVentilatorJpaEntity ventilator = findOrThrow(id);
        try {
            ventilatorRepository.delete(ventilator);
            // Flush inside the try block so the FK violation (e.g. evaluations
            // history) surfaces here instead of at commit time.
            ventilatorRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessLayerException(
                    "El ventilador tiene historial clínico asociado y no puede eliminarse; "
                            + "márquelo como MAINTENANCE para retirarlo de servicio",
                    HttpStatus.CONFLICT);
        }
    }

    private PhysicalVentilatorJpaEntity findOrThrow(UUID id) {
        return ventilatorRepository.findById(id)
                .orElseThrow(() -> new BusinessLayerException(
                        "No se encontró el ventilador con id: " + id, HttpStatus.NOT_FOUND));
    }
}
