package wfederico.pneumacare.shift.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shift.infrastructure.persistence.MedicalShiftJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.ShiftHandoverJpaEntity;
import wfederico.pneumacare.shift.infrastructure.persistence.audit.ShiftRevisionEntity;
import wfederico.pneumacare.shift.web.dto.AuditRevisionResponse;
import wfederico.pneumacare.shift.web.dto.HandoverResponse;
import wfederico.pneumacare.shift.web.dto.ShiftResponse;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reads Envers revision history for shift-context records (PNMC-134).
 *
 * <p>Each revision is read through the {@link AuditReader} together with its
 * {@link ShiftRevisionEntity} (actor + timestamp) and {@link RevisionType}, then mapped
 * to an {@link AuditRevisionResponse}. Revisions are returned oldest-first.
 */
@Service
public class ShiftAuditService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AuditRevisionResponse<ShiftResponse>> getShiftHistory(UUID shiftId) {
        return readHistory(MedicalShiftJpaEntity.class, shiftId, ShiftResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AuditRevisionResponse<HandoverResponse>> getHandoverHistory(UUID handoverId) {
        return readHistory(ShiftHandoverJpaEntity.class, handoverId, HandoverResponse::from);
    }

    @SuppressWarnings("unchecked")
    private <E, D> List<AuditRevisionResponse<D>> readHistory(
            Class<E> entityClass, UUID id, Function<E, D> mapper) {

        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<?> rows = reader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<AuditRevisionResponse<D>> history = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Object[] tuple = (Object[]) row;
            E snapshot = (E) tuple[0];
            ShiftRevisionEntity revision = (ShiftRevisionEntity) tuple[1];
            RevisionType type = (RevisionType) tuple[2];

            history.add(new AuditRevisionResponse<>(
                    revision.getRev(),
                    mapRevisionType(type),
                    revision.getActorId(),
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(revision.getRevtstmp()), ZoneOffset.UTC),
                    snapshot == null ? null : mapper.apply(snapshot)));
        }
        return history;
    }

    private static String mapRevisionType(RevisionType type) {
        return switch (type) {
            case ADD -> "CREATE";
            case MOD -> "UPDATE";
            case DEL -> "DELETE";
        };
    }
}
