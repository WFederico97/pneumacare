package wfederico.pneumacare.inventory.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.inventory.infrastructure.persistence.AssetAssignmentJpaEntity;
import wfederico.pneumacare.inventory.infrastructure.persistence.AssetAssignmentRepository;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorJpaEntity;
import wfederico.pneumacare.inventory.infrastructure.persistence.PhysicalVentilatorRepository;
import wfederico.pneumacare.inventory.web.dto.AssetAssignmentResponse;
import wfederico.pneumacare.inventory.web.dto.AssignAssetRequest;
import wfederico.pneumacare.inventory.web.dto.UnassignAssetRequest;
import wfederico.pneumacare.shared.exception.BusinessLayerException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Assigns and releases physical ventilators to/from patients.
 *
 * <p>Each operation runs in a single transaction so the assignment write and
 * the ventilator status transition are atomic. The ventilator status is the
 * source of truth for availability; the {@code asset_assignments} row is the
 * durable history of who held the hardware and when.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetAssignmentService {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final AssetAssignmentRepository assignmentRepository;
    private final PhysicalVentilatorRepository ventilatorRepository;

    @Transactional
    public AssetAssignmentResponse assign(AssignAssetRequest request) {
        PhysicalVentilatorJpaEntity ventilator = ventilatorRepository.findById(request.ventilatorId())
                .orElseThrow(() -> new BusinessLayerException(
                        "No se encontró el ventilador con id: " + request.ventilatorId(),
                        HttpStatus.NOT_FOUND));

        if (!assignmentRepository.patientExists(request.patientId())) {
            throw new BusinessLayerException(
                    "No se encontró el paciente con id: " + request.patientId(), HttpStatus.NOT_FOUND);
        }

        if (ventilator.getStatus() != VentilatorStatus.AVAILABLE) {
            throw new BusinessLayerException(
                    "El ventilador no está disponible (estado actual: " + ventilator.getStatus() + ")",
                    HttpStatus.BAD_REQUEST);
        }

        if (assignmentRepository.existsByPatientIdAndReleasedAtIsNull(request.patientId())) {
            throw new BusinessLayerException(
                    "El paciente ya tiene un ventilador asignado", HttpStatus.CONFLICT);
        }

        AssetAssignmentJpaEntity assignment = AssetAssignmentJpaEntity.builder()
                .ventilatorId(request.ventilatorId())
                .patientId(request.patientId())
                .assignedAt(OffsetDateTime.now())
                .assignedBy(resolveCurrentUser())
                .build();

        ventilator.setStatus(VentilatorStatus.IN_USE);

        try {
            // saveAndFlush so the partial unique indexes fire inside this block.
            AssetAssignmentJpaEntity saved = assignmentRepository.saveAndFlush(assignment);
            ventilatorRepository.save(ventilator);
            return AssetAssignmentResponse.from(saved, ventilator.getStatus());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessLayerException(
                    "El ventilador o el paciente ya tiene una asignación activa", HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public AssetAssignmentResponse unassign(UnassignAssetRequest request) {
        PhysicalVentilatorJpaEntity ventilator = ventilatorRepository.findById(request.ventilatorId())
                .orElseThrow(() -> new BusinessLayerException(
                        "No se encontró el ventilador con id: " + request.ventilatorId(),
                        HttpStatus.NOT_FOUND));

        AssetAssignmentJpaEntity assignment = assignmentRepository
                .findByVentilatorIdAndReleasedAtIsNull(request.ventilatorId())
                .orElseThrow(() -> new BusinessLayerException(
                        "El ventilador no tiene una asignación activa", HttpStatus.CONFLICT));

        assignment.setReleasedAt(OffsetDateTime.now());
        ventilator.setStatus(VentilatorStatus.AVAILABLE);

        AssetAssignmentJpaEntity saved = assignmentRepository.save(assignment);
        ventilatorRepository.save(ventilator);
        return AssetAssignmentResponse.from(saved, ventilator.getStatus());
    }

    /**
     * Resolves the authenticated user's UUID from the JWT {@code sub} claim.
     * Falls back to a nil UUID in dev (no JWT), mirroring
     * {@code EvaluationPersistenceService.resolveCreatedBy}.
     */
    private UUID resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return NIL_UUID;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            return NIL_UUID;
        }
    }
}
