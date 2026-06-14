package com.scholr.lms.billing;

/**
 * A normalized billing webhook from the payment processor — the processor's raw payload mapped into the
 * small set of facts the domain actually acts on. Keeping this a vendor-neutral value (not a Stripe event
 * object) is what keeps the PSP SDK out of {@link BillingService}.
 *
 * @param providerEventId the processor's immutable event id — the idempotency key for dedup
 * @param type            the normalized event type ({@code payment.succeeded}, {@code payment.failed},
 *                        {@code subscription.canceled})
 * @param subscriptionRef the processor's subscription id this event concerns
 */
public record WebhookEvent(String providerEventId, String type, String subscriptionRef) {
}
