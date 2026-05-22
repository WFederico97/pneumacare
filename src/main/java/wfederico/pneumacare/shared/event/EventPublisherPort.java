package wfederico.pneumacare.shared.event;

/**
 * Outbound port for publishing domain events within the monolith.
 * Implementations may use Spring's ApplicationEventPublisher, a local bus, etc.
 */
public interface EventPublisherPort {

    void publish(Object event);
}
