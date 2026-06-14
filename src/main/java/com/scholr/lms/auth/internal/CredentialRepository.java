package com.scholr.lms.auth.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.auth.domain.Credential;
import com.scholr.lms.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Global (not tenant-scoped) — login must resolve a user by email before any tenant context exists.
 * Tenant-filtered reads pass the tenant id explicitly (the table has no {@code @TenantId}).
 */
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByEmail(String email);

    List<Credential> findByTenantIdOrderByDisplayName(UUID tenantId);

    long countByTenantIdAndRole(UUID tenantId, Role role);

    boolean existsByEmail(String email);
}
