package com.scholr.lms.events.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * The consumer-side dedup record that makes a consumer <strong>idempotent</strong>. Tenant-scoped.
 *
 * <p>A streaming backbone delivers <em>at least once</em>: a consumer is guaranteed to see every
 * event, but it may see some more than once (a rebalance, a redelivery after a crash before the
 * offset was committed). Rather than chase the operational nightmare of true exactly-once delivery,
 * the pragmatic design is at-least-once delivery plus exactly-once <em>effects</em>: before acting on
 * an event, the consumer records its id here, guarded by a primary key. If the row already exists,
 * the event has been handled and is skipped. Same outcome as exactly-once, far less machinery.
 *
 * <p>The id is the event's own id (one consumer per row here for simplicity; a multi-consumer system
 * keys on {@code (consumer, event_id)}). This mirrors the idempotent-write discipline used for enroll
 * in Part 2 and submission in Part 4 — a unique key turns "did I already do this?" into a cheap insert.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
