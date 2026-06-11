package com.scholr.lms.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * The tenant identifier — the one value object every context must honor.
 * In a multi-tenant LMS almost nothing is global; this rides along with every
 * request and constrains every query. It is the platform's shared kernel.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "tenant id is required");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId random() {
        return new TenantId(UUID.randomUUID());
    }
}
