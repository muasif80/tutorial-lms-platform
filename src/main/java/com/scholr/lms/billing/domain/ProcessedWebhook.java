package com.scholr.lms.billing.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * The dedup record that makes webhook processing idempotent. Tenant-scoped.
 *
 * <p>A payment processor delivers webhooks <em>at least once</em> — the same {@code charge.succeeded} or
 * {@code invoice.payment_failed} can arrive twice (a retry after a slow {@code 200}, a replay). Acting on a
 * duplicate would grant an entitlement twice or email two receipts. Keyed on the processor's own immutable
 * event id, this row turns "did I already handle this webhook?" into a cheap primary-key check — the exact
 * idempotent-consumer pattern from Part 5's event pipeline, now guarding money instead of analytics.
 */
@Entity
@Table(name = "processed_webhooks")
public class ProcessedWebhook {

    /** The payment processor's event id (e.g. a Stripe event id). */
    @Id
    @Column(name = "provider_event_id")
    private String providerEventId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedWebhook() {
    }

    public ProcessedWebhook(String providerEventId, Instant processedAt) {
        this.providerEventId = providerEventId;
        this.processedAt = processedAt;
    }

    public String providerEventId() {
        return providerEventId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
