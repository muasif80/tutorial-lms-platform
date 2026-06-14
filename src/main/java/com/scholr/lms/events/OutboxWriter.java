package com.scholr.lms.events;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.scholr.lms.events.domain.OutboxEvent;
import com.scholr.lms.events.internal.OutboxEventRepository;
import org.springframework.stereotype.Component;

/**
 * Appends a domain event to the transactional outbox. Deliberately has <strong>no</strong> transaction
 * of its own: it is called from inside a business service's {@code @Transactional} method, so the event
 * row is inserted on the <em>same</em> connection and commits (or rolls back) atomically with the state
 * change that produced it. That atomicity is the entire point — it closes the dual-write gap where the
 * database and the event stream could diverge.
 *
 * <p>Shipping the event to the broker is a separate concern handled later by {@link OutboxRelay}, so a
 * slow or unavailable broker can never fail (or slow down) the business transaction.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxWriter(OutboxEventRepository outbox) {
        this(outbox, Clock.systemUTC());
    }

    OutboxWriter(OutboxEventRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Record an event. Must be called within an active business transaction.
     *
     * @param type        the event type, e.g. {@code enrollment.created}
     * @param aggregateId the aggregate the event is about (also the stream partition key)
     * @param payload     a self-contained JSON snapshot the consumer can act on without callbacks
     */
    public OutboxEvent append(String type, UUID aggregateId, String payload) {
        Instant now = Instant.now(clock);
        return outbox.save(OutboxEvent.of(type, aggregateId, payload, now));
    }
}
