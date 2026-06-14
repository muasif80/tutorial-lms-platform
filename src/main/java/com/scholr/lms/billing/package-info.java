/**
 * Billing bounded context: pricing plans, subscriptions, entitlements, and the payment-processor
 * integration that ties them to money. Implemented in Part 7.
 *
 * <p>Payments are a distributed-consistency problem wearing a money hat: the database and the payment
 * processor must agree, exactly once, or you double-charge or grant access nobody paid for. The context
 * answers that with {@link com.scholr.lms.billing.BillingService} — idempotent webhook handling keyed on
 * the processor's event id (a {@link com.scholr.lms.billing.domain.ProcessedWebhook} dedup record),
 * a {@link com.scholr.lms.billing.domain.Subscription} state machine as the source of truth, a separate
 * {@link com.scholr.lms.billing.domain.Entitlement} read on every access check, and a reconciliation pass
 * that diffs the database against the processor through the {@link com.scholr.lms.billing.PaymentGateway}
 * seam (Stripe plugs in there; the domain never imports a PSP SDK).
 *
 * <p>Tenant-scoped like every context, with money stored as integer minor units and access decisions
 * decoupled from billing internals.
 */
package com.scholr.lms.billing;
