package com.scholr.lms.media.internal;

import java.util.Optional;
import java.util.UUID;

import com.scholr.lms.media.domain.TranscodeJob;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface TranscodeJobRepository extends JpaRepository<TranscodeJob, UUID> {

    /** Backs idempotent enqueue: find-or-create keyed on the natural dedup key. */
    Optional<TranscodeJob> findByIdempotencyKey(String idempotencyKey);
}
