package com.scholr.lms.billing.internal;

import com.scholr.lms.billing.domain.ProcessedWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. Keyed on the processor's event id (String). */
public interface ProcessedWebhookRepository extends JpaRepository<ProcessedWebhook, String> {
}
