package com.scholr.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.scholr.lms.billing.BillingService;
import com.scholr.lms.billing.FakePaymentGateway;
import com.scholr.lms.billing.WebhookEvent;
import com.scholr.lms.billing.domain.Plan;
import com.scholr.lms.billing.domain.Subscription;
import com.scholr.lms.billing.domain.SubscriptionStatus;
import com.scholr.lms.identity.IdentityService;
import com.scholr.lms.identity.domain.AppUser;
import com.scholr.lms.identity.domain.Organization;
import com.scholr.lms.shared.TenantContext;
import com.scholr.lms.shared.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the Part 7 guarantees: webhook processing is idempotent (a retried event can't double-grant),
 * the subscription state machine drives entitlements correctly (granted while access is allowed, revoked
 * on cancel), reconciliation repairs database/processor drift, and billing is tenant-isolated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BillingTest {

    @Autowired private IdentityService identity;
    @Autowired private BillingService billing;
    @Autowired private FakePaymentGateway gateway;

    private static final String KEY = "all-access";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private record Fixture(UUID learnerId, Plan plan) {
    }

    private Fixture setUp(String orgName, String email) {
        Organization org = identity.createOrganization(orgName);
        TenantContext.set(TenantId.of(org.id()));
        AppUser learner = identity.createUser(email, "Learner");
        Plan plan = billing.createPlan("All Access", KEY, Plan.Interval.MONTH, 4900);
        return new Fixture(learner.id(), plan);
    }

    @Test
    void subscribing_grants_access_and_a_canceled_webhook_revokes_it() {
        Fixture f = setUp("Acme", "bill-l1@acme.test");
        Subscription sub = billing.subscribe(f.learnerId(), f.plan().id(), "sub_1", false);
        assertEquals(SubscriptionStatus.ACTIVE, sub.status());
        assertTrue(billing.hasAccess(f.learnerId(), KEY), "an active subscription grants access");

        billing.handleWebhook(new WebhookEvent("evt_cancel", "subscription.canceled", "sub_1"));
        assertFalse(billing.hasAccess(f.learnerId(), KEY), "cancellation revokes access");
    }

    @Test
    void webhook_processing_is_idempotent() {
        Fixture f = setUp("Acme", "bill-l2@acme.test");
        billing.subscribe(f.learnerId(), f.plan().id(), "sub_2", true); // trialing

        WebhookEvent paid = new WebhookEvent("evt_paid_1", "payment.succeeded", "sub_2");
        assertTrue(billing.handleWebhook(paid), "first delivery is applied");
        assertFalse(billing.handleWebhook(paid), "a duplicate delivery (same event id) is skipped");
        assertFalse(billing.handleWebhook(paid), "and skipped again — exactly-once effect");

        assertTrue(billing.hasAccess(f.learnerId(), KEY), "access is granted exactly once, not multiplied");
    }

    @Test
    void past_due_keeps_access_during_grace_then_recovers() {
        Fixture f = setUp("Acme", "bill-l3@acme.test");
        billing.subscribe(f.learnerId(), f.plan().id(), "sub_3", false);

        // A failed payment moves to PAST_DUE — access is RETAINED during the grace/dunning window.
        billing.handleWebhook(new WebhookEvent("evt_fail", "payment.failed", "sub_3"));
        assertTrue(billing.hasAccess(f.learnerId(), KEY), "PAST_DUE retains access during the grace window");

        // Dunning recovers the payment — back to ACTIVE.
        billing.handleWebhook(new WebhookEvent("evt_recover", "payment.succeeded", "sub_3"));
        assertTrue(billing.hasAccess(f.learnerId(), KEY), "recovery keeps access");
    }

    @Test
    void reconciliation_repairs_drift_from_the_processor() {
        Fixture f = setUp("Acme", "bill-l4@acme.test");
        Subscription sub = billing.subscribe(f.learnerId(), f.plan().id(), "sub_4", false);
        assertTrue(billing.hasAccess(f.learnerId(), KEY));

        // The processor canceled the subscription, but our webhook for it was lost — the DB still says ACTIVE.
        gateway.setProcessorStatus("sub_4", SubscriptionStatus.CANCELED);

        boolean repaired = billing.reconcile(sub.id());
        assertTrue(repaired, "reconciliation detects the divergence");
        assertFalse(billing.hasAccess(f.learnerId(), KEY), "and repairs it — access revoked to match the processor");

        // Running it again is a no-op now that DB and processor agree.
        assertFalse(billing.reconcile(sub.id()), "no drift remains");
    }

    @Test
    void billing_is_tenant_isolated() {
        Fixture acme = setUp("Acme", "bill-a@acme.test");
        billing.subscribe(acme.learnerId(), acme.plan().id(), "sub_acme", false);
        assertTrue(billing.hasAccess(acme.learnerId(), KEY));

        // A different tenant must not see Acme's entitlement, even for the same learner id space.
        Fixture globex = setUp("Globex", "bill-g@globex.test");
        assertFalse(billing.hasAccess(acme.learnerId(), KEY),
            "Globex's tenant context cannot see Acme's entitlement");
        assertFalse(billing.hasAccess(globex.learnerId(), KEY),
            "Globex's own learner has no subscription yet");
    }
}
