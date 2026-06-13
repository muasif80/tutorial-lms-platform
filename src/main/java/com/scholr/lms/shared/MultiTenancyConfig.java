package com.scholr.lms.shared;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Hibernate's discriminator multi-tenancy: every {@code @TenantId} column is
 * automatically filtered (on reads) and populated (on writes) using the tenant this
 * resolver returns.
 *
 * <p>We register the resolver explicitly through {@link HibernatePropertiesCustomizer}
 * rather than relying on bean auto-detection. The {@code @TenantId} annotation alone
 * flips the {@code SessionFactory} into multi-tenant mode, and if the resolver is not
 * handed to Hibernate at build time the very first session fails with
 * <em>"SessionFactory configured for multi-tenancy, but no tenant identifier specified."</em>
 * Wiring it via the customizer makes that contract explicit and order-independent.
 */
@Configuration
public class MultiTenancyConfig implements HibernatePropertiesCustomizer {

    /** Reserved tenant for non-request work (e.g. background jobs, startup, schema validation). */
    public static final UUID SYSTEM_TENANT = new UUID(0L, 0L);

    private final CurrentTenantIdentifierResolver<UUID> resolver = new TenantResolver();

    @Bean
    CurrentTenantIdentifierResolver<UUID> tenantIdentifierResolver() {
        return resolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    /**
     * Returns the request's tenant, falling back to {@link #SYSTEM_TENANT}. It must
     * never return {@code null}: Hibernate opens sessions before any request exists
     * (startup, repository bootstrap, background work) and rejects a null identifier.
     */
    static final class TenantResolver implements CurrentTenantIdentifierResolver<UUID> {

        @Override
        public UUID resolveCurrentTenantIdentifier() {
            TenantId tenant = TenantContext.get();
            return tenant != null ? tenant.value() : SYSTEM_TENANT;
        }

        @Override
        public boolean validateExistingCurrentSessions() {
            return false;
        }
    }
}
