package com.scholr.lms.billing;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.scholr.lms.billing.domain.SubscriptionStatus;
import org.springframework.stereotype.Component;

/**
 * An in-process {@link PaymentGateway} — the default so billing runs and is testable without a Stripe
 * account. It models the one capability reconciliation depends on: the processor is the authority on a
 * subscription's status, and our database is a replica that can drift. Tests set the "processor truth"
 * here, mutate the local replica, and assert that reconciliation heals the difference.
 *
 * <p>In production this bean is replaced by a Stripe-backed adapter; nothing in the {@code billing} domain
 * changes, because the domain only knows the {@link PaymentGateway} port.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    /** providerRef → the processor's authoritative status. */
    private final Map<String, SubscriptionStatus> processorTruth = new ConcurrentHashMap<>();

    @Override
    public String createCheckoutSession(String planProviderRef, UUID learnerId) {
        return "https://fake-psp.test/checkout/" + planProviderRef + "?learner=" + learnerId;
    }

    @Override
    public Optional<SubscriptionStatus> fetchStatus(String providerRef) {
        return Optional.ofNullable(processorTruth.get(providerRef));
    }

    /** Test/seam helper: set what the processor believes a subscription's status is. */
    public void setProcessorStatus(String providerRef, SubscriptionStatus status) {
        processorTruth.put(providerRef, status);
    }
}
