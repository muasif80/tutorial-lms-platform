package com.scholr.lms.billing.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

/**
 * The Subscription aggregate: one learner's ongoing relationship to a {@link Plan}, and the state machine
 * that billing events drive. Tenant-scoped, with a {@code @Version} optimistic lock so two concurrent
 * webhook deliveries can't both transition it.
 *
 * <p>The transitions are guarded so an out-of-order or nonsensical webhook can't corrupt state — you cannot
 * reactivate a canceled subscription, for instance. This is the same "the model enforces its own rules"
 * discipline as the seat invariant in Part 2 and the one-way submit in Part 4. It also carries the PSP's
 * own id ({@code providerRef}) so reconciliation can diff this row against the payment processor.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    /** The payment processor's subscription id (e.g. a Stripe sub id) — the key for reconciliation. */
    @Column(name = "provider_ref", nullable = false)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Version
    private long version;

    protected Subscription() {
    }

    public Subscription(UUID id, UUID learnerId, UUID planId, String providerRef, SubscriptionStatus status) {
        this.id = id;
        this.learnerId = learnerId;
        this.planId = planId;
        this.providerRef = providerRef;
        this.status = status;
    }

    public static Subscription start(UUID learnerId, UUID planId, String providerRef, boolean trial) {
        return new Subscription(UUID.randomUUID(), learnerId, planId, providerRef,
            trial ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE);
    }

    /** Access is granted in every state except CANCELED — PAST_DUE is the grace/dunning window. */
    public boolean grantsAccess() {
        return status != SubscriptionStatus.CANCELED;
    }

    /** Payment succeeded (initial or dunning recovery): trialing/past_due → active. No-op if canceled. */
    public void activate() {
        if (status == SubscriptionStatus.TRIALING || status == SubscriptionStatus.PAST_DUE
            || status == SubscriptionStatus.ACTIVE) {
            status = SubscriptionStatus.ACTIVE;
        }
        // a CANCELED subscription cannot be silently reactivated by a stray event
    }

    /** A payment failed: active → past_due (enters the grace/dunning window). No-op if canceled. */
    public void markPastDue() {
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING) {
            status = SubscriptionStatus.PAST_DUE;
        }
    }

    /** End the subscription (customer cancel, or dunning exhausted). One-way and terminal. */
    public void cancel() {
        status = SubscriptionStatus.CANCELED;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID learnerId() {
        return learnerId;
    }

    public UUID planId() {
        return planId;
    }

    public String providerRef() {
        return providerRef;
    }

    public SubscriptionStatus status() {
        return status;
    }
}
