package wfederico.backendjavacoretemplate.infra.adapter.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import wfederico.backendjavacoretemplate.core.messaging.KafkaTopics;
import wfederico.backendjavacoretemplate.domain.event.PlayerEvent;

/**
 * Kafka consumer for player domain events.
 * Placed in module-player infra layer because it is player-domain-specific.
 * Generic Kafka infrastructure (factories, templates) lives in platform-messaging.
 */
@Component
@Slf4j
public class PlayerEventConsumer {

    @KafkaListener(topics = KafkaTopics.PLAYER_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(PlayerEvent event) {
        log.info("Consumed player event: type={}, playerId={}, timestamp={}",
                event.getType(), event.getPlayerId(), event.getTimestamp());
    }
}

