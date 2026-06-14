package com.scholr.lms.billing;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.scholr.lms.billing.domain.Entitlement;
import com.scholr.lms.billing.domain.Plan;
import com.scholr.lms.billing.domain.ProcessedWebhook;
import com.scholr.lms.billing.domain.Subscription;
import com.scholr.lms.billing.domain.SubscriptionStatus;
import com.scholr.lms.billing.internal.EntitlementRepository;
import com.scholr.lms.billing.internal.PlanRepository;
import com.scholr.lms.billing.internal.ProcessedWebhookRepository;
import com.scholr.lms.billing.internal.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the Billing context. It turns billing events into two things the rest of the platform
 * cares about: a {@link Subscription} state machine (the source of truth) and an {@link Entitlement} (the
 * access record read on every request). The hard problems it solves are the ones the article is about:
 * idempotent webhook processing so a retried event can't double-grant, and reconciliation so the database
 * and the payment processor never silently disagree about who paid for what.
 */
@Service
public class BillingService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final EntitlementRepository entitlements;
    private final ProcessedWebhookRepository processedWebhooks;
    private final PaymentGateway gateway;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingService(PlanRepository plans, SubscriptionRepository subscriptions,
                          EntitlementRepository entitlements, ProcessedWebhookRepository processedWebhooks,
                          PaymentGateway gateway) {
        this(plans, subscriptions, entitlements, processedWebhooks, gateway, Clock.systemUTC());
    }

    BillingService(PlanRepository plans, SubscriptionRepository subscriptions,
                   EntitlementRepository entitlements, ProcessedWebhookRepository processedWebhooks,
                   PaymentGateway gateway, Clock clock) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.processedWebhooks = processedWebhooks;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    public Plan createPlan(String name, String entitlementKey, Plan.Interval interval, long priceCents) {
        return plans.save(Plan.of(name, entitlementKey, interval, priceCents));
    }

    /**
     * Begin a subscription: record it locally as TRIALING/ACTIVE and grant the entitlement. In a real flow
     * this follows a successful checkout; here it is the entry point the controller and tests use.
     */
    @Transactional
    public Subscription subscribe(UUID learnerId, UUID planId, String providerRef, boolean trial) {
        Plan plan = plans.findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("plan not found: " + planId));
        Subscription sub = subscriptions.save(Subscription.start(learnerId, planId, providerRef, trial));
        syncEntitlement(sub, plan);
        return sub;
    }

    /**
     * Handle a billing webhook — <strong>idempotently</strong>. A payment processor delivers at least once,
     * so the same event can arrive twice; the dedup record keyed on the processor's event id makes a
     * duplicate a no-op. On a first delivery, the event drives the subscription state machine and the
     * entitlement is re-synced to match. The dedup write and the state change commit together, so a crash
     * mid-handle redelivers and re-applies cleanly.
     *
     * @return {@code true} if the event was applied; {@code false} if it was a duplicate and skipped
     */
    @Transactional
    public boolean handleWebhook(WebhookEvent event) {
        if (processedWebhooks.existsById(event.providerEventId())) {
            return false; // already handled — at-least-once delivery, exactly-once effect
        }

        Subscription sub = subscriptions.findByProviderRef(event.subscriptionRef()).orElse(null);
        if (sub != null) {
            switch (event.type()) {
                case "payment.succeeded" -> sub.activate();
                case "payment.failed" -> sub.markPastDue();
                case "subscription.canceled" -> sub.cancel();
                default -> { /* an event type billing doesn't act on; still record it as processed */ }
            }
            subscriptions.save(sub);
            plans.findById(sub.planId()).ifPresent(plan -> syncEntitlement(sub, plan));
        }

        processedWebhooks.save(new ProcessedWebhook(event.providerEventId(), Instant.now(clock)));
        return true;
    }

    /** The access check, read on every protected request. Cheap by design — one indexed row. */
    @Transactional(readOnly = true)
    public boolean hasAccess(UUID learnerId, String entitlementKey) {
        return entitlements.findByLearnerIdAndEntitlementKey(learnerId, entitlementKey)
            .map(Entitlement::isActive)
            .orElse(false);
    }

    /**
     * Reconciliation — the real source of truth. Webhooks can be lost or arrive out of order, so the
     * database can drift from the processor. This compares one subscription against what the processor
     * actually reports and repairs the local state (and entitlement) to match. Run periodically over all
     * subscriptions, it heals any drift the event path missed.
     *
     * @return {@code true} if a divergence was found and repaired
     */
    @Transactional
    public boolean reconcile(UUID subscriptionId) {
        Subscription sub = subscriptions.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("subscription not found: " + subscriptionId));
        SubscriptionStatus processorStatus = gateway.fetchStatus(sub.providerRef()).orElse(null);
        if (processorStatus == null || processorStatus == sub.status()) {
            return false; // nothing to repair
        }
        // drive the local subscription to the processor's authoritative status
        switch (processorStatus) {
            case ACTIVE -> sub.activate();
            case PAST_DUE -> sub.markPastDue();
            case CANCELED -> sub.cancel();
            case TRIALING -> { /* trialing is only an initial state; nothing to force here */ }
        }
        subscriptions.save(sub);
        plans.findById(sub.planId()).ifPresent(plan -> syncEntitlement(sub, plan));
        return true;
    }

    /** Make the entitlement's active flag match whether the subscription currently grants access. */
    private void syncEntitlement(Subscription sub, Plan plan) {
        Entitlement ent = entitlements
            .findByLearnerIdAndEntitlementKey(sub.learnerId(), plan.entitlementKey())
            .orElseGet(() -> Entitlement.grant(sub.learnerId(), plan.entitlementKey(), sub.id()));
        ent.setActive(sub.grantsAccess());
        entitlements.save(ent);
    }
}
