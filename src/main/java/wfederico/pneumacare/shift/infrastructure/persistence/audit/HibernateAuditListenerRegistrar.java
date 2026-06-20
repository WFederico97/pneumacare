package wfederico.pneumacare.shift.infrastructure.persistence.audit;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link ClosedShiftAuditListener} with Hibernate's event system (PNMC-134).
 *
 * <p>Hibernate event listeners are not Spring beans, so the Spring-managed listener is
 * attached after context startup by unwrapping the {@link EntityManagerFactory} down to
 * the {@link SessionFactoryImplementor} and appending it to the {@code POST_INSERT} /
 * {@code POST_UPDATE} listener groups.
 */
@Configuration
@RequiredArgsConstructor
public class HibernateAuditListenerRegistrar {

    private final EntityManagerFactory entityManagerFactory;
    private final ClosedShiftAuditListener closedShiftAuditListener;

    @PostConstruct
    public void registerListeners() {
        SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry =
                sessionFactory.getServiceRegistry().requireService(EventListenerRegistry.class);

        registry.appendListeners(EventType.POST_INSERT, closedShiftAuditListener);
        registry.appendListeners(EventType.POST_UPDATE, closedShiftAuditListener);
    }
}
