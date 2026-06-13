package com.scholr.lms.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * Links a (global) user to an organization with a role. Tenant-scoped: the
 * {@code tenantId} is populated and filtered automatically by Hibernate via
 * {@link TenantId}, so a query can never accidentally read another org's memberships.
 */
@Entity
@Table(name = "memberships")
public class Membership {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected Membership() {
    }

    public Membership(UUID id, UUID userId, Role role) {
        this.id = id;
        this.userId = userId;
        this.role = role;
    }

    public static Membership create(UUID userId, Role role) {
        return new Membership(UUID.randomUUID(), userId, role);
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID userId() {
        return userId;
    }

    public Role role() {
        return role;
    }
}
