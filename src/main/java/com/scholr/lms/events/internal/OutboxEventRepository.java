package com.scholr.lms.events.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.events.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** The relay drains these — unshipped events, oldest first, so per-aggregate order is preserved. */
    List<OutboxEvent> findByPublishedFalseOrderByOccurredAtAsc();
}
