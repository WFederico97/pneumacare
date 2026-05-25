package wfederico.pneumacare.shared.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Primary {@link EventPublisherPort} adapter that routes domain events to Kafka topics.
 *
 * <p>Active only when {@code app.kafka.enabled=true}. When disabled,
 * {@link ApplicationEventPublisherAdapter} is used as an in-process fallback.
 *
 * <h2>Topic naming convention</h2>
 * The topic is derived from the event class name:
 * <ol>
 *   <li>Strip trailing {@code Event} suffix (if present)</li>
 *   <li>Convert CamelCase to kebab-case</li>
 *   <li>Prefix with {@code app.kafka.topics.prefix} (default: {@code pneumacare.events})</li>
 * </ol>
 * Examples:
 * <ul>
 *   <li>{@code PatientAdmittedEvent} → {@code pneumacare.events.patient-admitted}</li>
 *   <li>{@code AssessmentCompleted}  → {@code pneumacare.events.assessment-completed}</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * Send failures are logged but do not propagate synchronously; the Kafka producer's
 * internal retry and the DLT recoverer in {@link wfederico.pneumacare.shared.config.KafkaConfig}
 * handle retries on the consumer side.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.prefix:pneumacare.events}")
    private String topicPrefix;

    @Override
    public void publish(Object event) {
        String topic = resolveTopic(event);
        kafkaTemplate.send(topic, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to topic [{}]: {}",
                                event.getClass().getSimpleName(), topic, ex.getMessage(), ex);
                    } else {
                        log.debug("Published event [{}] → topic [{}] partition [{}] offset [{}]",
                                event.getClass().getSimpleName(),
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveTopic(Object event) {
        String name = event.getClass().getSimpleName();
        if (name.endsWith("Event")) {
            name = name.substring(0, name.length() - 5);
        }
        return topicPrefix + "." + toKebabCase(name);
    }

    private static String toKebabCase(String camelCase) {
        return camelCase
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
    }
}
