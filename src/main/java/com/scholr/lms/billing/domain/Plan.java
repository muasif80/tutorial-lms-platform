package com.scholr.lms.billing.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * A purchasable plan — what a subscription is a subscription <em>to</em>. Tenant-scoped.
 *
 * <p>A plan names the access it confers via {@code entitlementKey} (e.g. {@code all-access}, or a specific
 * course key), the billing {@code interval}, and the price in the smallest currency unit (cents) — money is
 * always integer minor units, never a floating-point dollar amount, because float rounding on money is how
 * you end up a cent off and unable to reconcile.
 */
@Entity
@Table(name = "billing_plans")
public class Plan {

    public enum Interval { ONE_TIME, MONTH, YEAR }

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    /** The access this plan grants — the key an {@link Entitlement} carries. */
    @Column(name = "entitlement_key", nullable = false)
    private String entitlementKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false)  // 'interval' is a reserved word in Postgres/H2
    private Interval interval;

    /** Price in minor units (cents). Integer money only — never a float. */
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    protected Plan() {
    }

    public Plan(UUID id, String name, String entitlementKey, Interval interval, long priceCents) {
        if (priceCents < 0) {
            throw new IllegalArgumentException("priceCents must be >= 0");
        }
        this.id = id;
        this.name = name;
        this.entitlementKey = entitlementKey;
        this.interval = interval;
        this.priceCents = priceCents;
    }

    public static Plan of(String name, String entitlementKey, Interval interval, long priceCents) {
        return new Plan(UUID.randomUUID(), name, entitlementKey, interval, priceCents);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public String entitlementKey() {
        return entitlementKey;
    }

    public Interval interval() {
        return interval;
    }

    public long priceCents() {
        return priceCents;
    }
}
