package com.scholr.lms.events;

import java.util.List;

import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.events.internal.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ships outbox events to the broker. In production this role is played by change-data-capture
 * (Debezium tailing the outbox table's WAL) or a polling publisher on a schedule; here it is a simple
 * polling relay you call to drain the table — same contract, no infrastructure.
 *
 * <p>It is deliberately <strong>at-least-once</strong>. It publishes an event and then marks it
 * published; if the process dies after the publish but before the mark, the event is re-shipped on the
 * next run. That is the correct trade-off: never lose an event, accept the occasional duplicate, and
 * make consumers idempotent so a duplicate is harmless. Chasing exactly-once delivery here would buy
 * fragility for a guarantee the consumer side already provides more cheaply (see
 * {@link com.scholr.lms.events.domain.ProcessedEvent}).
 *
 * <p>Events are drained oldest-first, so a consumer reading a given aggregate's events sees them in the
 * order they occurred.
 */
@Component
public class OutboxRelay {

    private final OutboxEventRepository outbox;
    private final EventPublisher publisher;

    public OutboxRelay(OutboxEventRepository outbox, EventPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    /**
     * Drain all currently-unpublished events to the broker.
     *
     * @return the number of events shipped on this pass
     */
    @Transactional
    public int relayPending() {
        List<OutboxEvent> pending = outbox.findByPublishedFalseOrderByOccurredAtAsc();
        for (OutboxEvent event : pending) {
            publisher.publish(event);   // at-least-once: ship first...
            event.markPublished();      // ...then mark; a crash between the two re-ships, never loses
        }
        outbox.saveAll(pending);
        return pending.size();
    }
}
