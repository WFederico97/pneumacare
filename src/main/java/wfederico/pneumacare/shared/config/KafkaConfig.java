package wfederico.pneumacare.shared.config;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka infrastructure configuration.
 *
 * <p>Registers a {@link ConcurrentKafkaListenerContainerFactory} that delegates
 * all Spring-Boot defaults (observation, tracing, concurrency) to
 * {@link ConcurrentKafkaListenerContainerFactoryConfigurer} and then attaches a
 * {@link DefaultErrorHandler} with:
 * <ul>
 *   <li>3 retries, 1 s apart (FixedBackOff)</li>
 *   <li>Failed records routed to the originating topic's {@code .DLT} topic</li>
 * </ul>
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /**
     * Listener container factory with dead-letter-topic (DLT) error recovery.
     *
     * <p>Uses {@link ConcurrentKafkaListenerContainerFactoryConfigurer} to inherit
     * all Spring Boot auto-configuration (Micrometer observations, tracing bridge,
     * AckMode, etc.) before applying custom error handling.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);

        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1_000L, 3L)
        ));

        return factory;
    }
}
