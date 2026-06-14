package com.scholr.lms.web;

import java.util.UUID;

import com.scholr.lms.billing.BillingService;
import com.scholr.lms.billing.WebhookEvent;
import com.scholr.lms.billing.domain.Plan;
import com.scholr.lms.billing.domain.Subscription;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the Billing context: create plans, subscribe, receive payment-processor webhooks, and
 * check access. The webhook endpoint is the one that matters most — it is idempotent, so the processor can
 * safely retry it, which is exactly what a payment processor does.
 */
@RestController
@RequestMapping("/api")
public class BillingController {

    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    public record CreatePlan(String name, String entitlementKey, Plan.Interval interval, long priceCents) {
    }

    public record PlanView(UUID id, String name, String entitlementKey, long priceCents) {
    }

    @PostMapping("/billing/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanView createPlan(@RequestBody CreatePlan req) {
        Plan p = billing.createPlan(req.name(), req.entitlementKey(), req.interval(), req.priceCents());
        return new PlanView(p.id(), p.name(), p.entitlementKey(), p.priceCents());
    }

    public record Subscribe(UUID learnerId, UUID planId, String providerRef, boolean trial) {
    }

    public record SubscriptionView(UUID id, UUID learnerId, String status) {
    }

    @PostMapping("/billing/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView subscribe(@RequestBody Subscribe req) {
        Subscription s = billing.subscribe(req.learnerId(), req.planId(), req.providerRef(), req.trial());
        return new SubscriptionView(s.id(), s.learnerId(), s.status().name());
    }

    /** The payment processor calls this; it is idempotent, so retries are safe. */
    @PostMapping("/billing/webhooks")
    public WebhookResult webhook(@RequestBody WebhookEvent event) {
        boolean applied = billing.handleWebhook(event);
        return new WebhookResult(applied);
    }

    public record WebhookResult(boolean applied) {
    }

    public record AccessView(boolean hasAccess) {
    }

    @GetMapping("/billing/access")
    public AccessView access(@RequestParam UUID learnerId, @RequestParam String entitlementKey) {
        return new AccessView(billing.hasAccess(learnerId, entitlementKey));
    }
}
