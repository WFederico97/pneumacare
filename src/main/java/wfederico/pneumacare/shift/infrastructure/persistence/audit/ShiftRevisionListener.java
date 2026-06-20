package wfederico.pneumacare.shift.infrastructure.persistence.audit;

import org.hibernate.envers.RevisionListener;
import wfederico.pneumacare.shared.security.AuthenticatedActor;

/**
 * Stamps each Envers revision with the acting user (PNMC-134).
 *
 * <p>Hibernate instantiates this listener directly, so it cannot receive injected
 * Spring beans; the actor is therefore resolved through the static
 * {@link AuthenticatedActor} helper, which reads the {@code SecurityContextHolder}.
 * This mirrors the prior art in {@code EvaluationPersistenceService.resolveCreatedBy()}.
 */
public class ShiftRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((ShiftRevisionEntity) revisionEntity).setActorId(AuthenticatedActor.currentActorId());
    }
}
