package com.scholr.lms.events.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * One domain event, written to the <strong>transactional outbox</strong> in the very same database
 * transaction as the business state that produced it. Tenant-scoped.
 *
 * <p>This single row is the whole answer to the dual-write problem. A naive design writes the
 * business change, commits, and <em>then</em> publishes to the broker — and if that second step
 * fails or the process dies between them, the database and the event stream disagree forever
 * (the warehouse says a course is incomplete that the learner finished). By persisting the event
 * <em>with</em> the state change, the two commit or roll back together: there is no window in which
 * one happened and the other didn't. A separate relay later ships unpublished rows to the broker
 * and flips {@link #published}, so delivery is decoupled from the business transaction.
 *
 * <p>Following the series rule, the payload is a self-contained JSON snapshot referenced by ids,
 * not a JPA association, so a consumer never has to reach back into the producing aggregate.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The event type, e.g. {@code enrollment.created} or {@code assessment.submitted}. */
    @Column(nullable = false, updatable = false)
    private String type;

    /** The id of the aggregate this event is about — also the partition key for ordering. */
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    /** A self-contained JSON snapshot of the event. Consumers read this, not the source aggregate. */
    @Column(nullable = false, updatable = false, length = 4000)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Flipped true once the relay has shipped this event to the broker (at-least-once). */
    @Column(nullable = false)
    private boolean published;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String type, UUID aggregateId, String payload, Instant occurredAt) {
        this.id = id;
        this.type = type;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.published = false;
    }

    public static OutboxEvent of(String type, UUID aggregateId, String payload, Instant occurredAt) {
        return new OutboxEvent(UUID.randomUUID(), type, aggregateId, payload, occurredAt);
    }

    public void markPublished() {
        this.published = true;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String type() {
        return type;
    }

    public UUID aggregateId() {
        return aggregateId;
    }

    public String payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public boolean isPublished() {
        return published;
    }
}
