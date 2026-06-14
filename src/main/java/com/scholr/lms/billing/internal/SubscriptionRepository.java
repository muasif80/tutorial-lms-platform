package com.scholr.lms.billing.internal;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.billing.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** Find by the payment processor's id — the seam webhooks and reconciliation resolve a subscription through. */
    Optional<Subscription> findByProviderRef(String providerRef);
}
