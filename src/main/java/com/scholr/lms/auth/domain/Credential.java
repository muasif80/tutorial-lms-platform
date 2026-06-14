package com.scholr.lms.auth.domain;

import java.util.UUID;

import com.scholr.lms.identity.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A login credential — the record Spring Security authenticates against. <strong>Global, not
 * tenant-scoped</strong>: login happens before any tenant context exists, so this table must be
 * queryable by email without a tenant filter (it has no {@code @TenantId}). On a successful login the
 * authenticated principal carries the user's {@code tenantId}, which is then set into the request's
 * tenant context so every subsequent query is correctly scoped — the same isolation guarantee as the
 * rest of the platform, now driven by the authenticated identity instead of a trusted header.
 *
 * <p>The {@code role} (reusing the identity {@link Role}: LEARNER / INSTRUCTOR / ORG_ADMIN) is the
 * RBAC anchor — Spring Security maps it to a granted authority, and the UI routes are gated on it.
 * The password is stored only as a BCrypt hash; the plaintext never touches the database.
 */
@Entity
@Table(name = "credentials")
public class Credential {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** The user's home organization (tenant); set into the tenant context on login. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** The domain user this credential authenticates (the {@code app_users} id). */
    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected Credential() {
    }

    public Credential(UUID id, String email, String passwordHash, Role role,
                      UUID tenantId, UUID appUserId, String displayName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.tenantId = tenantId;
        this.appUserId = appUserId;
        this.displayName = displayName;
    }

    public static Credential of(String email, String passwordHash, Role role,
                                UUID tenantId, UUID appUserId, String displayName) {
        return new Credential(UUID.randomUUID(), email, passwordHash, role, tenantId, appUserId, displayName);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID appUserId() {
        return appUserId;
    }

    public String displayName() {
        return displayName;
    }
}
