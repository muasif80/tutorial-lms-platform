package com.scholr.lms.catalog.internal;

import java.util.UUID;

import com.scholr.lms.catalog.domain.MediaBlob;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface MediaBlobRepository extends JpaRepository<MediaBlob, UUID> {
}
