package wfederico.pneumacare.shared.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Fallback {@link EventPublisherPort} adapter using Spring's in-process
 * {@link ApplicationEventPublisher}.
 *
 * <p>Active only when no other {@link EventPublisherPort} bean is present in the
 * context — i.e. when {@code app.kafka.enabled=false} (local dev without Kafka).
 * In production/staging the {@link KafkaEventPublisherAdapter} takes precedence.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(EventPublisherPort.class)
public class ApplicationEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        applicationEventPublisher.publishEvent(event);
    }
}
