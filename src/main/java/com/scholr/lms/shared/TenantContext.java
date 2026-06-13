package com.scholr.lms.shared;

/**
 * Holds the current request's tenant. Set by the web tier (from the authenticated
 * request) and read by the persistence tier's tenant resolver. The whole platform's
 * isolation rests on this being set correctly on every request.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantId tenant) {
        CURRENT.set(tenant);
    }

    public static TenantId get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
