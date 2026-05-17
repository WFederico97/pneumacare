package wfederico.backendjavacoretemplate.core.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Object event) {
        publish(KafkaTopics.PLAYER_EVENTS, event);
    }

    public void publish(String topic, Object event) {
        log.info("Publishing event to topic [{}]: {}", topic, event);
        kafkaTemplate.send(topic, event);
    }
}

