package com.scholr.lms.billing;

import java.util.Optional;

import com.scholr.lms.billing.domain.SubscriptionStatus;

/**
 * The payment-processor seam. Stripe (or Adyen, Braintree, Paddle…) plugs in here; the {@code billing}
 * domain never imports a processor SDK. This is the same agnostic principle the series applies everywhere —
 * the LLM provider, the broker, the search engine — now applied to the PSP, so the domain depends on an
 * intent ("start a checkout", "what does the processor think this subscription's status is?") rather than
 * on one vendor's API surface. Swapping processors, or running a fake in tests, changes only the adapter.
 */
public interface PaymentGateway {

    /** Begin a hosted checkout for a plan and return a URL to redirect the learner to. */
    String createCheckoutSession(String planProviderRef, java.util.UUID learnerId);

    /**
     * The processor's current view of a subscription's status — the authority reconciliation diffs against.
     * Empty if the processor has no such subscription.
     */
    Optional<SubscriptionStatus> fetchStatus(String providerRef);
}
