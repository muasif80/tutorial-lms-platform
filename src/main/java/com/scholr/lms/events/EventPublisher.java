package com.scholr.lms.events;

import com.scholr.lms.events.domain.OutboxEvent;

/**
 * The port the outbox relay ships events through — the seam a real broker plugs into. The in-process
 * {@link InMemoryEventPublisher} implements it for tests and single-node dev; in production this is
 * backed by Kafka/Redpanda (or change-data-capture via Debezium reading the outbox table directly).
 *
 * <p>Whatever the backing, the publisher must partition by the event's aggregate id so that all events
 * for one learner (or one tenant) land on the same partition and are therefore delivered <em>in order</em>
 * — per-key ordering is the one ordering guarantee a partitioned log gives you, and it is exactly the
 * one progress computation needs.
 */
public interface EventPublisher {

    /** Publish one event to the stream, partitioned by its aggregate id. */
    void publish(OutboxEvent event);
}
