package com.scholr.lms.shared;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate's discriminator multi-tenancy: every {@code @TenantId} column is
 * automatically filtered (on reads) and populated (on writes) using the tenant this
 * resolver returns. Spring Boot auto-detects the {@link CurrentTenantIdentifierResolver}
 * bean and hands it to Hibernate.
 */
@Configuration
public class MultiTenancyConfig {

    /** Reserved tenant for non-request work (e.g. background jobs, startup). */
    public static final UUID SYSTEM_TENANT = new UUID(0L, 0L);

    @Bean
    CurrentTenantIdentifierResolver<UUID> tenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolver<>() {
            @Override
            public UUID resolveCurrentTenantIdentifier() {
                TenantId tenant = TenantContext.get();
                return tenant != null ? tenant.value() : SYSTEM_TENANT;
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return false;
            }
        };
    }
}
