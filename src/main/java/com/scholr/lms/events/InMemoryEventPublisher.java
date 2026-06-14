package com.scholr.lms.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.scholr.lms.events.domain.OutboxEvent;
import org.springframework.stereotype.Component;

/**
 * An in-process {@link EventPublisher} — the default so the pipeline runs and is testable without a
 * Kafka cluster. It models the two properties a partitioned log must provide: every published event is
 * retained (so a consumer can read them), and events for the same aggregate id keep their relative order
 * (per-partition ordering).
 *
 * <p>In production this bean is replaced by a {@code KafkaEventPublisher}; nothing above this port
 * changes. Storing events in per-partition lists keyed by aggregate id is deliberately the same mental
 * model as a log's partitions.
 */
@Component
public class InMemoryEventPublisher implements EventPublisher {

    /** aggregateId (partition key) → ordered events, mimicking a partitioned log. */
    private final Map<UUID, List<OutboxEvent>> partitions = new ConcurrentHashMap<>();

    @Override
    public void publish(OutboxEvent event) {
        partitions.computeIfAbsent(event.aggregateId(), k -> new CopyOnWriteArrayList<>()).add(event);
    }

    /** All events published for an aggregate, in order — what a consumer of that partition would read. */
    public List<OutboxEvent> partition(UUID aggregateId) {
        return partitions.getOrDefault(aggregateId, List.of());
    }

    /** Total events published across all partitions (test/observability helper). */
    public int totalPublished() {
        return partitions.values().stream().mapToInt(List::size).sum();
    }
}
