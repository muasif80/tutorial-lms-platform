package com.scholr.lms.billing.internal;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.billing.domain.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

    /** The hot path: does this learner hold this entitlement? Read on every access check. */
    Optional<Entitlement> findByLearnerIdAndEntitlementKey(UUID learnerId, String entitlementKey);
}
