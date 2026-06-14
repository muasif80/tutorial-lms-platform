package com.scholr.lms.billing.domain;

/**
 * The subscription lifecycle state machine. Access is granted in every state except {@link #CANCELED} —
 * note that {@link #PAST_DUE} still grants access, because it is the <em>grace window</em> during which
 * dunning retries the payment; you revoke only when dunning is exhausted and the subscription cancels.
 */
public enum SubscriptionStatus {

    /** In a free trial; access granted, no payment taken yet. */
    TRIALING,

    /** Paid and current; access granted. */
    ACTIVE,

    /** A payment failed; access still granted during the grace/dunning window while retries run. */
    PAST_DUE,

    /** Ended — by the customer, or because dunning was exhausted. Access revoked. Terminal. */
    CANCELED
}
