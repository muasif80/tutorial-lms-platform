package com.scholr.lms.events.internal;

import java.util.UUID;

import com.scholr.lms.events.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /** Has this event already been handled? The cheap existence check at the heart of an idempotent consumer. */
    boolean existsById(UUID eventId);
}
