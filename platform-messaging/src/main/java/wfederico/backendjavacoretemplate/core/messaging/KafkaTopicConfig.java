package wfederico.backendjavacoretemplate.core.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic playerEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PLAYER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

