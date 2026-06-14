package com.scholr.lms.billing.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.TenantId;

/**
 * The access-granting record: "this learner may access this thing." Tenant-scoped, and deliberately
 * <strong>separate from the subscription</strong>.
 *
 * <p>Separating entitlements from subscriptions is the design call the article argues for. The entitlement
 * is the small, hot record read on <em>every</em> course-access request, so it must be cheap to check and
 * independent of billing internals; the subscription is the source of truth that <em>drives</em> it. Billing
 * events flip {@code active}; access control only ever reads this row. The decoupling also lets a single
 * entitlement be granted by different sources over time (a subscription now, a one-time purchase or a
 * manual comp later) without the access check caring which.
 */
@Entity
@Table(
    name = "entitlements",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "learner_id", "entitlement_key"})
)
public class Entitlement {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;

    /** What this grants — matches a {@link Plan}'s entitlementKey (e.g. {@code all-access}). */
    @Column(name = "entitlement_key", nullable = false, updatable = false)
    private String entitlementKey;

    @Column(nullable = false)
    private boolean active;

    /** The subscription currently backing this entitlement (for audit/reconciliation). */
    @Column(name = "source_subscription_id")
    private UUID sourceSubscriptionId;

    protected Entitlement() {
    }

    public Entitlement(UUID id, UUID learnerId, String entitlementKey, boolean active, UUID sourceSubscriptionId) {
        this.id = id;
        this.learnerId = learnerId;
        this.entitlementKey = entitlementKey;
        this.active = active;
        this.sourceSubscriptionId = sourceSubscriptionId;
    }

    public static Entitlement grant(UUID learnerId, String entitlementKey, UUID sourceSubscriptionId) {
        return new Entitlement(UUID.randomUUID(), learnerId, entitlementKey, true, sourceSubscriptionId);
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public String entitlementKey() {
        return entitlementKey;
    }

    public boolean isActive() {
        return active;
    }

    public UUID sourceSubscriptionId() {
        return sourceSubscriptionId;
    }
}
