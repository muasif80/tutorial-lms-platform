package com.scholr.lms.media.internal;

import java.util.List;
import java.util.UUID;

import com.scholr.lms.media.domain.VideoAsset;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically by Hibernate {@code @TenantId}. */
public interface VideoAssetRepository extends JpaRepository<VideoAsset, UUID> {

    /** Tenant-scoped query: only the current tenant's assets for a course are returned. */
    List<VideoAsset> findByCourseId(UUID courseId);
}
